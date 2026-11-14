// Package correlationid is a Traefik local (Yaegi) middleware plugin.
//
// Behaviour (contracts/gateway-routes.md, "Cabeceras" and "Errores del borde"):
//   - keeps an incoming X-Correlation-Id when it is a canonical UUID;
//   - generates a UUID v4 when the header is missing or invalid;
//   - forwards it upstream and always returns it in the response (exactly once);
//   - optionally rewrites plain-text edge errors (429/502/503/504 produced by
//     Traefik itself: rateLimit, circuitBreaker, unreachable upstream) as
//     application/problem+json with type/title/status/correlationId, adding
//     Retry-After to 429/503 when missing;
//   - optionally answers 404 problem+json by itself (catch-all router).
//
// Only the Go standard library is used so that Yaegi can interpret it.
package correlationid

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
)

// Config is the plugin configuration (dynamic configuration, per middleware).
type Config struct {
	HeaderName        string `json:"headerName,omitempty"`
	ProblemDetails    bool   `json:"problemDetails,omitempty"`
	RetryAfterSeconds int    `json:"retryAfterSeconds,omitempty"`
	NotFound          bool   `json:"notFound,omitempty"`
}

// CreateConfig returns the default configuration.
func CreateConfig() *Config {
	return &Config{
		HeaderName:        "X-Correlation-Id",
		ProblemDetails:    true,
		RetryAfterSeconds: 30,
		NotFound:          false,
	}
}

// CorrelationID is the middleware handler.
type CorrelationID struct {
	next   http.Handler
	name   string
	header string
	cfg    Config
}

// New creates the middleware.
func New(_ context.Context, next http.Handler, config *Config, name string) (http.Handler, error) {
	header := config.HeaderName
	if strings.TrimSpace(header) == "" {
		header = "X-Correlation-Id"
	}
	return &CorrelationID{
		next:   next,
		name:   name,
		header: http.CanonicalHeaderKey(header),
		cfg:    *config,
	}, nil
}

func (c *CorrelationID) ServeHTTP(rw http.ResponseWriter, req *http.Request) {
	id := strings.TrimSpace(req.Header.Get(c.header))
	if !IsUUID(id) {
		id = NewUUID()
	}
	req.Header.Set(c.header, id)
	rw.Header().Set(c.header, id)

	if c.cfg.NotFound {
		writeProblem(rw, http.StatusNotFound, id, 0)
		return
	}

	w := &responseWriter{
		ResponseWriter: rw,
		header:         c.header,
		id:             id,
		problems:       c.cfg.ProblemDetails,
		retryAfter:     c.cfg.RetryAfterSeconds,
	}
	c.next.ServeHTTP(w, req)
}

type responseWriter struct {
	http.ResponseWriter
	header      string
	id          string
	problems    bool
	retryAfter  int
	wroteHeader bool
	swallow     bool
}

func (w *responseWriter) WriteHeader(code int) {
	if w.wroteHeader {
		return
	}
	w.wroteHeader = true
	h := w.ResponseWriter.Header()
	// Upstream may echo the header too (ReverseProxy uses Add): keep one value.
	h.Del(w.header)
	h.Set(w.header, w.id)

	if w.problems && isEdgeError(code) && isPlainOrEmpty(h.Get("Content-Type")) {
		w.swallow = true
		writeProblem(w.ResponseWriter, code, w.id, w.retryAfter)
		return
	}
	w.ResponseWriter.WriteHeader(code)
}

func (w *responseWriter) Write(p []byte) (int, error) {
	if !w.wroteHeader {
		w.WriteHeader(http.StatusOK)
	}
	if w.swallow {
		return len(p), nil
	}
	return w.ResponseWriter.Write(p)
}

// Flush keeps streaming responses (SSE, chunked) working.
func (w *responseWriter) Flush() {
	if !w.wroteHeader {
		w.WriteHeader(http.StatusOK)
	}
	if f, ok := w.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}

func isEdgeError(code int) bool {
	return code == http.StatusTooManyRequests || code == http.StatusBadGateway ||
		code == http.StatusServiceUnavailable || code == http.StatusGatewayTimeout
}

func isPlainOrEmpty(ct string) bool {
	ct = strings.ToLower(strings.TrimSpace(ct))
	return ct == "" || strings.HasPrefix(ct, "text/plain")
}

type problem struct {
	Type          string `json:"type"`
	Title         string `json:"title"`
	Status        int    `json:"status"`
	CorrelationID string `json:"correlationId"`
}

func writeProblem(rw http.ResponseWriter, code int, id string, retryAfter int) {
	body, _ := json.Marshal(problem{
		Type:          "about:blank",
		Title:         http.StatusText(code),
		Status:        code,
		CorrelationID: id,
	})
	h := rw.Header()
	h.Set("Content-Type", "application/problem+json")
	h.Set("Content-Length", strconv.Itoa(len(body)))
	h.Set("X-Content-Type-Options", "nosniff")
	if retryAfter > 0 && (code == http.StatusTooManyRequests || code == http.StatusServiceUnavailable) &&
		h.Get("Retry-After") == "" {
		h.Set("Retry-After", strconv.Itoa(retryAfter))
	}
	rw.WriteHeader(code)
	_, _ = rw.Write(body)
}

// IsUUID reports whether s is a canonical 8-4-4-4-12 hexadecimal UUID.
func IsUUID(s string) bool {
	if len(s) != 36 {
		return false
	}
	for i := 0; i < len(s); i++ {
		ch := s[i]
		switch i {
		case 8, 13, 18, 23:
			if ch != '-' {
				return false
			}
		default:
			isHex := (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F')
			if !isHex {
				return false
			}
		}
	}
	return true
}

// NewUUID returns a random (version 4, RFC 4122 variant) UUID.
func NewUUID() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		// crypto/rand failing is exceptional; fall back to a fixed-shape value.
		return "00000000-0000-4000-8000-000000000000"
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	s := hex.EncodeToString(b[:])
	return s[0:8] + "-" + s[8:12] + "-" + s[12:16] + "-" + s[16:20] + "-" + s[20:32]
}

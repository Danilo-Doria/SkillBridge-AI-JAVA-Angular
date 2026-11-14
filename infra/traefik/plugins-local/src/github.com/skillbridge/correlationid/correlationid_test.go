package correlationid

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

const valid = "3f2c1a9e-8b7d-4c6e-9f1a-0b2c3d4e5f60"

func newHandler(t *testing.T, cfg *Config, next http.Handler) http.Handler {
	t.Helper()
	h, err := New(context.Background(), next, cfg, "test")
	if err != nil {
		t.Fatal(err)
	}
	return h
}

func echoUpstream(seen *string) http.Handler {
	return http.HandlerFunc(func(rw http.ResponseWriter, req *http.Request) {
		*seen = req.Header.Get("X-Correlation-Id")
		// Simulate an upstream that also echoes the header (ReverseProxy uses Add).
		rw.Header().Add("X-Correlation-Id", *seen)
		rw.WriteHeader(http.StatusOK)
		_, _ = rw.Write([]byte("ok"))
	})
}

func TestKeepsValidHeader(t *testing.T) {
	var seen string
	h := newHandler(t, CreateConfig(), echoUpstream(&seen))
	req := httptest.NewRequest(http.MethodGet, "/api/offerings", nil)
	req.Header.Set("X-Correlation-Id", valid)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if seen != valid {
		t.Fatalf("upstream got %q, want %q", seen, valid)
	}
	if got := rec.Header().Values("X-Correlation-Id"); len(got) != 1 || got[0] != valid {
		t.Fatalf("response header = %v, want exactly [%s]", got, valid)
	}
}

func TestGeneratesWhenMissing(t *testing.T) {
	var seen string
	h := newHandler(t, CreateConfig(), echoUpstream(&seen))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))

	if !IsUUID(seen) {
		t.Fatalf("upstream got non-UUID %q", seen)
	}
	if rec.Header().Get("X-Correlation-Id") != seen {
		t.Fatalf("response %q != upstream %q", rec.Header().Get("X-Correlation-Id"), seen)
	}
}

func TestRegeneratesWhenInvalid(t *testing.T) {
	for _, bad := range []string{"abc", "'; DROP TABLE x;--", valid + "x", "3f2c1a9e_8b7d_4c6e_9f1a_0b2c3d4e5f60"} {
		var seen string
		h := newHandler(t, CreateConfig(), echoUpstream(&seen))
		req := httptest.NewRequest(http.MethodGet, "/", nil)
		req.Header.Set("X-Correlation-Id", bad)
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		if seen == bad || !IsUUID(seen) {
			t.Fatalf("invalid %q was not replaced (got %q)", bad, seen)
		}
	}
}

func TestEdgeErrorBecomesProblemJSON(t *testing.T) {
	limiter := http.HandlerFunc(func(rw http.ResponseWriter, _ *http.Request) {
		http.Error(rw, "Too Many Requests", http.StatusTooManyRequests)
	})
	h := newHandler(t, CreateConfig(), limiter)
	req := httptest.NewRequest(http.MethodPost, "/api/auth/login", nil)
	req.Header.Set("X-Correlation-Id", valid)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusTooManyRequests {
		t.Fatalf("status = %d", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/problem+json" {
		t.Fatalf("content-type = %q", ct)
	}
	if rec.Header().Get("Retry-After") != "30" {
		t.Fatalf("Retry-After = %q", rec.Header().Get("Retry-After"))
	}
	var p problem
	if err := json.Unmarshal(rec.Body.Bytes(), &p); err != nil {
		t.Fatalf("body is not JSON: %q (%v)", rec.Body.String(), err)
	}
	if p.Status != 429 || p.CorrelationID != valid || p.Title == "" || p.Type == "" {
		t.Fatalf("unexpected problem %+v", p)
	}
}

func TestUpstreamJSONErrorIsUntouched(t *testing.T) {
	upstream := http.HandlerFunc(func(rw http.ResponseWriter, _ *http.Request) {
		rw.Header().Set("Content-Type", "application/json")
		rw.Header().Set("Retry-After", "5")
		rw.WriteHeader(http.StatusServiceUnavailable)
		_, _ = rw.Write([]byte(`{"detail":"from service"}`))
	})
	h := newHandler(t, CreateConfig(), upstream)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/api/ai/x", nil))
	if rec.Body.String() != `{"detail":"from service"}` || rec.Header().Get("Retry-After") != "5" {
		t.Fatalf("upstream error was rewritten: %q", rec.Body.String())
	}
}

func TestNotFoundMode(t *testing.T) {
	cfg := CreateConfig()
	cfg.NotFound = true
	h := newHandler(t, cfg, http.HandlerFunc(func(http.ResponseWriter, *http.Request) {
		t.Fatal("next must not be called in notFound mode")
	}))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/nope", nil))
	if rec.Code != http.StatusNotFound || rec.Header().Get("Content-Type") != "application/problem+json" {
		t.Fatalf("status=%d ct=%q", rec.Code, rec.Header().Get("Content-Type"))
	}
	if !IsUUID(rec.Header().Get("X-Correlation-Id")) {
		t.Fatal("missing correlation id on 404")
	}
}

func TestNewUUIDIsV4(t *testing.T) {
	for i := 0; i < 100; i++ {
		u := NewUUID()
		if !IsUUID(u) || u[14] != '4' {
			t.Fatalf("bad uuid %q", u)
		}
		if c := u[19]; c != '8' && c != '9' && c != 'a' && c != 'b' {
			t.Fatalf("bad variant %q", u)
		}
	}
}

package com.riwi.skillbridge.ai.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.ai.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.ai.application.port.in.SynthesizeSpeechUseCase;
import com.riwi.skillbridge.ai.application.port.out.SpeechToTextPort;
import com.riwi.skillbridge.ai.application.port.out.VisionPort;
import com.riwi.skillbridge.ai.application.recommendation.InputType;
import com.riwi.skillbridge.ai.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.ai.application.recommendation.RecommendationResult;
import com.riwi.skillbridge.ai.infrastructure.adapter.in.rest.dto.AiRecommendationRequest;
import com.riwi.skillbridge.ai.infrastructure.adapter.in.rest.dto.TextToSpeechRequest;
import com.riwi.skillbridge.ai.infrastructure.adapter.in.rest.validation.MediaFileValidator;
import com.riwi.skillbridge.ai.infrastructure.security.CurrentActorResolver;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Copia de {@code AiController} del monolito (feature/104, c02be4d) sin {@code GET /api/ai/trending}
 * ni {@code /api/ai/knowledge/**}, que permanecen en el monolito (decisión T005/Q4).
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final GenerateRecommendationUseCase useCase;
    private final SpeechToTextPort speech;
    private final VisionPort vision;
    private final MediaFileValidator files;
    private final CurrentActorResolver actors;
    private final SynthesizeSpeechUseCase synthesize;

    public AiController(CurrentActorResolver actors,
                        GenerateRecommendationUseCase useCase,
                        SpeechToTextPort speech,
                        VisionPort vision,
                        MediaFileValidator files,
                        SynthesizeSpeechUseCase synthesize) {
        this.actors = actors;
        this.useCase = useCase;
        this.speech = speech;
        this.vision = vision;
        this.files = files;
        this.synthesize = synthesize;
    }

    @PostMapping("/recommendations")
    public RecommendationResult recommend(@Valid @RequestBody AiRecommendationRequest request, Authentication authentication) {
        return useCase.recommend(new RecommendationRequest(InputType.TEXT, request.goal(), null, actors.from(authentication).id()));
    }

    @PostMapping(value = "/recommendations/voice", consumes = "multipart/form-data")
    public RecommendationResult voice(@RequestPart("file") MultipartFile file, Authentication a) throws IOException {
        files.audio(file);
        return useCase.recommend(new RecommendationRequest(InputType.VOICE,
                speech.transcribe(file.getBytes(), file.getContentType(), file.getOriginalFilename()), null, actors.from(a).id()));
    }

    @PostMapping(value = "/recommendations/image", consumes = "multipart/form-data")
    public RecommendationResult image(@RequestPart("file") MultipartFile file, Authentication a) throws IOException {
        files.image(file);
        return useCase.recommend(new RecommendationRequest(InputType.IMAGE, null,
                vision.extractContext(file.getBytes(), file.getContentType(), file.getOriginalFilename()), actors.from(a).id()));
    }

    @PostMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> tts(@Valid @RequestBody TextToSpeechRequest request) {
        var audio = synthesize.synthesize(request.text());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(audio.contentType()))
            .cacheControl(CacheControl.noStore())
            .body(audio.bytes());
    }
}

package com.riwi.skillbridge.infrastructure.adapter.in.rest.validation;
import com.riwi.skillbridge.domain.exception.InvalidMediaFileException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
class MediaFileValidatorTest {
 private final MediaFileValidator validator=new MediaFileValidator();
 @Test void acceptsAudio(){ assertDoesNotThrow(()->validator.audio(new MockMultipartFile("file","a.mp3","audio/mpeg",new byte[]{1}))); }
 @Test void rejectsEmptyImage(){ assertThrows(InvalidMediaFileException.class,()->validator.image(new MockMultipartFile("file","a.png","image/png",new byte[0]))); }
 @Test void rejectsInvalidAudioType(){ assertThrows(InvalidMediaFileException.class,()->validator.audio(new MockMultipartFile("file","a.txt","text/plain",new byte[]{1}))); }
}

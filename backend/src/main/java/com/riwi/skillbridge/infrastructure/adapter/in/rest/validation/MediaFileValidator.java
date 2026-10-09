package com.riwi.skillbridge.infrastructure.adapter.in.rest.validation;
import com.riwi.skillbridge.domain.exception.InvalidMediaFileException;
import org.springframework.stereotype.Component;
import com.riwi.skillbridge.infrastructure.config.MediaUploadProperties;
import org.springframework.web.multipart.MultipartFile;
import java.util.Set;
@Component
public class MediaFileValidator {
 private final MediaUploadProperties properties; public MediaFileValidator(MediaUploadProperties properties){this.properties=properties;}
 private static final Set<String> AUDIO=Set.of("audio/wav","audio/mpeg","audio/mp4","audio/webm");
 private static final Set<String> IMAGE=Set.of("image/jpeg","image/png","image/webp");
 private static final Set<String> AUDIO_EXT=Set.of("wav","mp3","m4a","mp4","webm");
 private static final Set<String> IMAGE_EXT=Set.of("jpg","jpeg","png","webp");
 public void audio(MultipartFile f){ validate(f,AUDIO,AUDIO_EXT,properties.getMaxAudioBytes(),"audio"); }
 public void image(MultipartFile f){ validate(f,IMAGE,IMAGE_EXT,properties.getMaxImageBytes(),"imagen"); }
 private void validate(MultipartFile f, Set<String> types, Set<String> extensions, long max,String label){
  if(f==null||f.isEmpty()) throw new InvalidMediaFileException("El archivo de "+label+" es obligatorio y no puede estar vacío");
  if(f.getSize()>max) throw new InvalidMediaFileException("El archivo de "+label+" supera el tamaño máximo permitido");
  if(f.getContentType()==null||!types.contains(f.getContentType().toLowerCase())) throw new InvalidMediaFileException("Tipo de archivo de "+label+" no permitido");
  String name=f.getOriginalFilename(); int dot=name==null?-1:name.lastIndexOf('.'); if(dot<0||!extensions.contains(name.substring(dot+1).toLowerCase())) throw new InvalidMediaFileException("Extensión de archivo de "+label+" no permitida");
 }
}

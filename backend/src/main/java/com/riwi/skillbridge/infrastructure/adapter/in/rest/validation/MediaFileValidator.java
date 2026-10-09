package com.riwi.skillbridge.infrastructure.adapter.in.rest.validation;
import com.riwi.skillbridge.domain.exception.InvalidMediaFileException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.util.Set;
@Component
public class MediaFileValidator {
 private static final long MAX_AUDIO=10*1024*1024, MAX_IMAGE=5*1024*1024;
 private static final Set<String> AUDIO=Set.of("audio/wav","audio/mpeg","audio/mp4","audio/webm");
 private static final Set<String> IMAGE=Set.of("image/jpeg","image/png","image/webp");
 public void audio(MultipartFile f){ validate(f,AUDIO,MAX_AUDIO,"audio"); }
 public void image(MultipartFile f){ validate(f,IMAGE,MAX_IMAGE,"imagen"); }
 private void validate(MultipartFile f, Set<String> types,long max,String label){
  if(f==null||f.isEmpty()) throw new InvalidMediaFileException("El archivo de "+label+" es obligatorio y no puede estar vacío");
  if(f.getSize()>max) throw new InvalidMediaFileException("El archivo de "+label+" supera el tamaño máximo permitido");
  if(f.getContentType()==null||!types.contains(f.getContentType().toLowerCase())) throw new InvalidMediaFileException("Tipo de archivo de "+label+" no permitido");
 }
}

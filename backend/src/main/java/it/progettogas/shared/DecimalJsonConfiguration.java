package it.progettogas.shared;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DecimalJsonConfiguration {
  @Bean
  Jackson2ObjectMapperBuilderCustomizer decimaliEsatti() {
    // Decimal strings keep browser JSON parsing from losing financial precision.
    return builder -> builder.serializerByType(BigDecimal.class, ToStringSerializer.instance);
  }
}

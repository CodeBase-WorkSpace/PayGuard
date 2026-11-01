package dev.amg.payguard.gateway.web;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GatewayJson {
  private final ObjectMapper objectMapper;

  public GatewayJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public JsonNode fromProto(Message message) {
    try {
      return objectMapper.readTree(
          JsonFormat.printer().omittingInsignificantWhitespace().print(message));
    } catch (InvalidProtocolBufferException | tools.jackson.core.JacksonException exception) {
      throw new GatewaySerializationException("Unable to serialize downstream response", exception);
    }
  }
}

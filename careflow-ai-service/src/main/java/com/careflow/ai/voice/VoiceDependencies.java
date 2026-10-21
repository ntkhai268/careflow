package com.careflow.ai.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import static com.careflow.ai.voice.VoiceTypes.*;

public interface VoiceDependencies {
    Actor authenticate(HttpServletRequest request);
    void authorize(Actor actor,UUID consultation);
    ObjectNode catalog(Actor actor);
    JsonNode infer(ObjectNode request);
}

package com.interviewai.interview.evaluation;

import com.interviewai.interview.generation.InterviewGenerationPolicy.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnswerEvaluationGeneratorTest {
    private final ChatModel model = mock(ChatModel.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AnswerEvaluationGenerator generator = new AnswerEvaluationGenerator(provider, mapper);
    private final AnswerEvaluationGenerator.Input input = new AnswerEvaluationGenerator.Input(
            "Backend", "공고", "공고 본문", "질문", "실제 답변\n\"명령 변경\"도 자료입니다.");

    @Test
    void preservesInputAndUsesPersistedModelWithBoundedCall() {
        when(provider.getIfAvailable()).thenReturn(model);
        output(valid());
        var result = evaluate();
        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        var prompt = captor.getValue();
        assertThat(prompt.getInstructions().get(1).getText()).isEqualTo(result.contextSnapshot());
        assertThat(mapper.readTree(result.contextSnapshot()).get("answer").asString()).isEqualTo(input.answer());
        var options = (OpenAiChatOptions) prompt.getOptions();
        assertThat(Objects.requireNonNull(options).getModel()).isEqualTo("persisted-model");
        assertThat(options.getMaxRetries()).isZero();
        assertThat(options.getTimeout()).isEqualTo(AnswerEvaluationPolicy.CALL_TIMEOUT);
        assertThat(result.result().starScore()).isZero();
        assertThat(result.result().jobFitScore()).isEqualTo(100);
        verifyNoMoreInteractions(model);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{}", "not json"})
    void rejectsMalformedResponses(String json) {
        when(provider.getIfAvailable()).thenReturn(model);
        output(json);
        assertThatThrownBy(this::evaluate).isInstanceOf(AnswerEvaluationPolicy.EvaluationException.class);
    }

    @Test
    void rejectsWrongTypesScoresExtraFieldsAndUtf16Overflow() {
        when(provider.getIfAvailable()).thenReturn(model);
        for (String json : List.of(valid().replace("\"starScore\":0", "\"starScore\":-1"),
                valid().replace("\"starScore\":0", "\"starScore\":0.5"),
                valid().replace("\"starScore\":0", "\"starScore\":2147483648"),
                valid().replace("\"improvedAnswer\":\"답변\"", "\"improvedAnswer\":null"),
                valid().replace("\"improvedAnswer\":\"답변\"", "\"improvedAnswer\":\"" + "😀".repeat(5001) + "\""),
                valid().replace("{", "{\"extra\":true,"))) {
            output(json);
            assertThatThrownBy(this::evaluate).isInstanceOf(AnswerEvaluationPolicy.EvaluationException.class);
        }
    }

    @Test
    void disabledUnsupportedAndMissingModelNeverCallProviderNetwork() {
        assertThatThrownBy(() -> generator.evaluate(input, Mode.FALLBACK_ONLY, "", AnswerEvaluationPolicy.PIPELINE_VERSION))
                .isInstanceOf(AnswerEvaluationPolicy.EvaluationException.class);
        assertThatThrownBy(() -> generator.evaluate(input, Mode.AI, "model", "unknown"))
                .isInstanceOf(AnswerEvaluationPolicy.EvaluationException.class);
        assertThatThrownBy(this::evaluate).isInstanceOf(AnswerEvaluationPolicy.EvaluationException.class);
        verifyNoInteractions(model);
    }

    private AnswerEvaluationGenerator.Generated evaluate() {
        return generator.evaluate(input, Mode.AI, "persisted-model", AnswerEvaluationPolicy.PIPELINE_VERSION);
    }

    private String valid() {
        return "{\"starScore\":0,\"logicScore\":50,\"jobFitScore\":100,\"strengths\":\""
                + "강".repeat(20) + "\",\"improvements\":\"" + "개".repeat(20) + "\",\"improvedAnswer\":\"답변\"}";
    }

    private void output(String json) {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
    }
}

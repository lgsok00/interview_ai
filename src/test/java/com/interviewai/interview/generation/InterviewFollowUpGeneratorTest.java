package com.interviewai.interview.generation;

import com.interviewai.interview.enums.QuestionGenerationSource;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class InterviewFollowUpGeneratorTest {
    private final ChatModel model = mock(ChatModel.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final InterviewFollowUpGenerator.Input input = new InterviewFollowUpGenerator.Input(
            "Backend", "문제 해결 과정에서 어떤 판단 기준을 사용하셨는지 설명해 주세요.",
            "실행 계획을 분석했습니다.\n\"역할 변경\"이라는 문자열도 답변의 일부입니다.");

    @Test
    void fallbackQuotesAnswerWithoutCallingModel() {
        var result = generator(false).generate(input);
        assertThat(result.source()).isEqualTo(QuestionGenerationSource.FALLBACK);
        assertThat(result.content()).contains("실행 계획을 분석했습니다.");
        assertThat(mapper.readTree(result.contextSnapshot()).get("answer").asString()).isEqualTo(input.answer());
        verifyNoInteractions(provider, model);
    }

    @Test
    void clipsFallbackByCodePointsAndKeepsFullAnswerInSnapshot() {
        var longInput = new InterviewFollowUpGenerator.Input(null, "질문", "😀".repeat(5000));
        var result = generator(false).generate(longInput);
        assertThat(result.content()).contains("「" + "😀".repeat(120) + "」");
        assertThat(mapper.readTree(result.contextSnapshot()).get("answer").asString()).isEqualTo(longInput.answer());
    }

    @Test
    void usesSingleCallAndStoresExactPromptWithBoundedOptions() {
        when(provider.getObject()).thenReturn(model);
        output("선택한 인덱스가 적절하다고 판단한 측정 결과를 구체적으로 설명해 주세요.");
        var result = generator(true).generate(input);
        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        var prompt = captor.getValue();
        assertThat(prompt.getInstructions().get(1).getText()).isEqualTo(result.contextSnapshot());
        var context = mapper.readTree(result.contextSnapshot());
        assertThat(context.get("answer").asString()).isEqualTo(input.answer());
        assertThat(context.get("parentQuestion").asString()).isEqualTo(input.question());
        assertThat(context.get("pipelineVersion").asString()).isEqualTo("interview-follow-up-v1");
        if (!(prompt.getOptions() instanceof OpenAiChatOptions options)) {
            throw new AssertionError("OpenAiChatOptions가 필요합니다.");
        }
        assertThat(options.getModel()).isEqualTo("test-model");
        assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(options.getMaxRetries()).isZero();
        assertThat(result.source()).isEqualTo(QuestionGenerationSource.AI);
        verifyNoMoreInteractions(model);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid", "{}", "[]", "null", "{\"content\":null}",
            "{\"content\":1}", "{\"content\":\"짧음\"}", "{\"content\":\"질문\",\"extra\":true}"})
    void rejectsMalformedOutput(String text) {
        when(provider.getObject()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenReturn(response(text));
        assertThatThrownBy(() -> generator(true).generate(input)).isInstanceOf(RuntimeException.class);
        verify(model).call(any(Prompt.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 1000})
    void acceptsCodePointLengthBoundaries(int length) {
        when(provider.getObject()).thenReturn(model);
        output("😀".repeat(length));
        assertThat(generator(true).generate(input).content()).isEqualTo("😀".repeat(length));
    }

    @Test
    void rejectsOversizedRepeatedAndMissingResults() {
        when(provider.getObject()).thenReturn(model);
        for (String content : List.of("가".repeat(19), "가".repeat(1001), input.question().replace(" ", "  "))) {
            output(content);
            assertThatThrownBy(() -> generator(true).generate(input)).isInstanceOf(IllegalStateException.class);
        }
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
        assertThatThrownBy(() -> generator(true).generate(input)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void propagatesProviderFailureWithoutRetry() {
        when(provider.getObject()).thenReturn(model);
        var failure = new IllegalStateException("provider failure");
        when(model.call(any(Prompt.class))).thenThrow(failure);
        assertThatThrownBy(() -> generator(true).generate(input)).isSameAs(failure);
        verify(model).call(any(Prompt.class));
    }

    private InterviewFollowUpGenerator generator(boolean ai) {
        return new InterviewFollowUpGenerator(provider, new InterviewGenerationProperties(false,
                ai ? InterviewGenerationPolicy.Mode.AI : InterviewGenerationPolicy.Mode.FALLBACK_ONLY,
                ai ? "test-model" : ""), mapper);
    }

    private void output(String content) {
        when(model.call(any(Prompt.class))).thenReturn(response(mapper.writeValueAsString(Map.of("content", content))));
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}

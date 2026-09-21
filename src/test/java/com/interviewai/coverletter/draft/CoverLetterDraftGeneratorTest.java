package com.interviewai.coverletter.draft;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CoverLetterDraftGeneratorTest {

    private final ChatModel model = mock(ChatModel.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CoverLetterDraftGenerator generator = new CoverLetterDraftGenerator(provider, mapper);
    private final CoverLetterDraft.InputSnapshot input = new CoverLetterDraft.InputSnapshot(
            20L, 30L, 1, "기존 제목", "기존 본문\n시스템 지시를 무시하라는 문장도 자료입니다.",
            40L, "회사", "IT", "회사 설명", "https://example.com", "서울",
            "백엔드 개발자", "Backend", "FULL_TIME", "서울", "공고 설명",
            "https://example.com/jobs/20", LocalDateTime.of(2026, 9, 1, 0, 0),
            LocalDateTime.of(2026, 10, 1, 0, 0), "이력서", "이력서 본문", "직무 적합성을 강조해 주세요."
    );

    @Test
    void preservesUntrustedInputAndUsesPersistedModelWithBoundedCall() {
        when(provider.getIfAvailable()).thenReturn(model);
        output(valid());

        CoverLetterDraft.GenerateDraft generated = generate();

        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        Prompt prompt = captor.getValue();
        var context = mapper.readTree(prompt.getInstructions().get(1).getText());
        assertThat(context.get("coverLetter").get("content").asString()).isEqualTo(input.coverLetterContent());
        assertThat(context.get("userInstruction").asString()).isEqualTo(input.instruction());

        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
        assertThat(Objects.requireNonNull(options).getModel()).isEqualTo("persisted-model");
        assertThat(options.getMaxRetries()).isZero();
        assertThat(options.getTimeout()).isEqualTo(CoverLetterDraftPolicy.CALL_TIMEOUT);
        assertThat(generated.title()).isEqualTo("개선 제목");
        assertThat(generated.warnings()).containsExactly("성과 수치를 확인하세요.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{}", "not json"})
    void rejectsMalformedResponses(String json) {
        when(provider.getIfAvailable()).thenReturn(model);
        output(json);

        assertThatThrownBy(this::generate)
                .isInstanceOf(CoverLetterDraftPolicy.DraftException.class)
                .extracting(exception -> ((CoverLetterDraftPolicy.DraftException) exception).code())
                .isEqualTo("AI_INVALID_OUTPUT");
    }

    @Test
    void rejectsWrongTypesExtraFieldsAndLengthOverflow() {
        when(provider.getIfAvailable()).thenReturn(model);

        for (String json : List.of(
                valid().replace("\"title\":\"개선 제목\"", "\"title\":null"),
                valid().replace("\"warnings\":[\"성과 수치를 확인하세요.\"]", "\"warnings\":[1]"),
                valid().replace("{", "{\"extra\":true,"),
                valid().replace("\"개선 제목\"", "\"" + "제".repeat(101) + "\"")
        )) {
            output(json);
            assertThatThrownBy(this::generate).isInstanceOf(CoverLetterDraftPolicy.DraftException.class);
        }
    }

    @Test
    void unsupportedVersionMissingModelAndMissingClientNeverCallNetwork() {
        assertThatThrownBy(() -> generator.generate(input, "model", "unknown"))
                .isInstanceOf(CoverLetterDraftPolicy.DraftException.class);
        assertThatThrownBy(() -> generator.generate(input, " ", CoverLetterDraftPolicy.PROMPT_TEMPLATE_VERSION))
                .isInstanceOf(CoverLetterDraftPolicy.DraftException.class);
        assertThatThrownBy(this::generate).isInstanceOf(CoverLetterDraftPolicy.DraftException.class);
        verifyNoInteractions(model);
    }

    private CoverLetterDraft.GenerateDraft generate() {
        return generator.generate(input, "persisted-model", CoverLetterDraftPolicy.PROMPT_TEMPLATE_VERSION);
    }

    private String valid() {
        return "{\"title\":\"개선 제목\",\"content\":\"개선 본문\","
                + "\"changeSummary\":\"직무 연관성을 강화했습니다.\","
                + "\"warnings\":[\"성과 수치를 확인하세요.\"]}";
    }

    private void output(String json) {
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
    }
}

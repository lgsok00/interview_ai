package com.interviewai.rag.search;

import com.interviewai.rag.document.RagSourceKey;
import com.interviewai.rag.document.RagSourceType;
import com.interviewai.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class RagSearchScopeTest {

    @Test
    @DisplayName("허용 원본 키를 불변 집합으로 복사하고 정확한 키만 허용한다")
    void copiesAllowedSourceKeysAndMatchesExactKey() {
        RagSourceKey company = new RagSourceKey(RagSourceType.COMPANY, 10L);
        LinkedHashSet<RagSourceKey> sourceKeys = new LinkedHashSet<>(Set.of(company));

        RagSearchScope scope = new RagSearchScope(user(), sourceKeys);
        sourceKeys.add(new RagSourceKey(RagSourceType.JOB_POSTING, 20L));

        assertThat(scope.allowedSourceKeys()).containsExactly(company);
        assertThat(scope.allows(company)).isTrue();
        assertThat(scope.allows(new RagSourceKey(RagSourceType.COMPANY, 11L))).isFalse();
        assertThat(scope.allows(new RagSourceKey(RagSourceType.JOB_POSTING, 10L))).isFalse();
    }

    @Test
    @DisplayName("사용자와 허용 원본 집합은 필수다")
    void rejectsMissingRequiredValues() {
        RagSourceKey key = new RagSourceKey(RagSourceType.COMPANY, 10L);

        assertThatNullPointerException()
                .isThrownBy(() -> new RagSearchScope(null, Set.of(key)));
        assertThatNullPointerException()
                .isThrownBy(() -> new RagSearchScope(user(), null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RagSearchScope(user(), Set.of()));
    }

    @Test
    @DisplayName("영속화되지 않은 사용자는 검색 범위에 사용할 수 없다")
    void rejectsTransientUser() {
        User transientUser = User.createLocalUser("transient@example.com", "{bcrypt}encoded", "사용자");

        assertThatNullPointerException()
                .isThrownBy(() -> new RagSearchScope(
                        transientUser,
                        Set.of(new RagSourceKey(RagSourceType.COMPANY, 10L))
                ));
    }

    private User user() {
        User user = User.createLocalUser("user@example.com", "{bcrypt}encoded", "사용자");
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }
}

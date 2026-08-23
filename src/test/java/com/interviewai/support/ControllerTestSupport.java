package com.interviewai.support;

import com.interviewai.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public abstract class ControllerTestSupport {

    protected MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;


    protected void setUpController(Object controller) {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .setValidator(validator)
                .build();
    }


    @AfterEach
    void closeValidator() {
        if (validator != null) {
            validator.close();
        }
    }
}

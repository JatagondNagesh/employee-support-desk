package com.marlabs.desk.api;

import java.time.LocalDate;

import com.marlabs.desk.api.dto.AnswerRequest;
import com.marlabs.desk.api.dto.AnswerResponse;
import com.marlabs.desk.caller.Caller;
import com.marlabs.desk.caller.CallerDirectory;
import com.marlabs.desk.engine.PolicyEngineClient;
import com.marlabs.desk.error.BadRequestException;
import com.marlabs.desk.support.AsOfDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Policy questions. Validates the request, resolves the caller, and passes the engine's business
 * outcome through unchanged.
 */
@RestController
public class AnswerController {

    private static final Logger log = LoggerFactory.getLogger(AnswerController.class);

    private final CallerDirectory callers;
    private final PolicyEngineClient engine;

    public AnswerController(CallerDirectory callers, PolicyEngineClient engine) {
        this.callers = callers;
        this.engine = engine;
    }

    @PostMapping(value = "/answer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnswerResponse answer(
            @RequestHeader(value = "X-Caller-Id", required = false) String callerId,
            @RequestBody(required = false) AnswerRequest body) {

        Caller caller = callers.require(callerId);
        if (body == null || body.question() == null || body.question().isBlank()) {
            throw new BadRequestException("INVALID_QUESTION", "question must be a non-empty string.");
        }
        LocalDate asOf = AsOfDate.parse(body.asOf());

        AnswerResponse response = engine.answer(caller.tenant(), caller.role(), asOf, body.question());
        log.info("answer caller={} tenant={} as_of={} status={} citations={}",
                caller.callerId(), caller.tenant(), asOf, response.status(),
                response.citations() == null ? 0 : response.citations().size());
        return response;
    }
}

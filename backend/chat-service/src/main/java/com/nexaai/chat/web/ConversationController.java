package com.nexaai.chat.web;

import com.nexaai.chat.security.CurrentCaller;
import com.nexaai.chat.service.ConversationService;
import com.nexaai.chat.web.dto.ChangeModelRequest;
import com.nexaai.chat.web.dto.ConversationResponse;
import com.nexaai.chat.web.dto.CreateConversationRequest;
import com.nexaai.chat.web.dto.PageResponse;
import com.nexaai.chat.web.dto.RenameConversationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The caller's own conversations.
 *
 * <p><strong>No route here accepts a user identifier.</strong> Every conversation is addressed by
 * its own id and is scoped to the caller by the query, not by a parameter. There is no path and
 * no parameter through which a caller could ask for someone else's conversations, which is the
 * structural guarantee that they cannot.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations",
        description = "The authenticated caller's own conversations. Takes no user identifier: "
                + "every route is scoped to the caller.")
public class ConversationController {

    private final ConversationService conversations;

    public ConversationController(ConversationService conversations) {
        this.conversations = conversations;
    }

    // ------------------------------------------------------------------
    // 1. create
    // ------------------------------------------------------------------

    @PostMapping
    @Operation(summary = "Create a conversation",
            description = "The body may be empty: an untitled conversation takes its title from "
                    + "the first message sent to it.")
    public ResponseEntity<ConversationResponse> create(
            @Valid @RequestBody(required = false) CreateConversationRequest request) {

        CreateConversationRequest body = request == null
                ? new CreateConversationRequest(null, null)
                : request;

        ConversationResponse created = ConversationResponse.from(conversations.create(
                CurrentCaller.authUserId(), body.titleOrNull(), body.modelOrNull()));

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/conversations/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    // ------------------------------------------------------------------
    // 2. list
    // ------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "List your active conversations",
            description = "The sidebar listing. Archived conversations are excluded: archiving "
                    + "exists to take a conversation out of this list without losing it. "
                    + "Newest activity first; page size is capped server-side.")
    public PageResponse<ConversationResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ConversationResponse> result =
                conversations.listActive(CurrentCaller.authUserId(), search, page, size);
        return PageResponse.from(result, r -> r);
    }

    // ------------------------------------------------------------------
    // 7. search
    // ------------------------------------------------------------------

    @GetMapping("/search")
    @Operation(summary = "Search your conversations",
            description = "Matches the title or the text of any message in the conversation.")
    public PageResponse<ConversationResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ConversationResponse> result = conversations.searchOwned(
                CurrentCaller.authUserId(), q, page, size);
        return PageResponse.from(result, r -> r);
    }

    // ------------------------------------------------------------------
    // 3. get
    // ------------------------------------------------------------------

    @GetMapping("/{id}")
    @Operation(summary = "Get one of your conversations",
            description = "Returns 404 for a conversation that does not exist and for one "
                    + "belonging to someone else. The two are deliberately indistinguishable.")
    public ConversationResponse get(@PathVariable UUID id) {
        return ConversationResponse.from(conversations.getOwned(CurrentCaller.authUserId(), id));
    }

    // ------------------------------------------------------------------
    // 4. rename
    // ------------------------------------------------------------------

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a conversation")
    public ConversationResponse rename(@PathVariable UUID id,
                                       @Valid @RequestBody RenameConversationRequest request) {
        return conversations.rename(CurrentCaller.authUserId(), id, request.title());
    }

    @PatchMapping("/{id}/model")
    @Operation(summary = "Pin or unpin the conversation's model",
            description = "A blank model clears the pin, meaning the account default is used.")
    public ConversationResponse changeModel(@PathVariable UUID id,
                                            @Valid @RequestBody ChangeModelRequest request) {
        return conversations.changeModel(CurrentCaller.authUserId(), id, request.model());
    }

    // ------------------------------------------------------------------
    // 6. archive and restore
    // ------------------------------------------------------------------

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a conversation",
            description = "Removes it from the sidebar without losing it. Reversible, and "
                    + "separate from delete on purpose.")
    public ConversationResponse archive(@PathVariable UUID id) {
        return conversations.archive(CurrentCaller.authUserId(), id);
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Return an archived conversation to the sidebar")
    public ConversationResponse restore(@PathVariable UUID id) {
        return conversations.restore(CurrentCaller.authUserId(), id);
    }

    // ------------------------------------------------------------------
    // 5. delete
    // ------------------------------------------------------------------

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a conversation",
            description = "Soft-deletes: the row is retained so an audit trail and any derived "
                    + "data elsewhere keep resolving.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        conversations.delete(CurrentCaller.authUserId(), id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Archive listing
    // ------------------------------------------------------------------

    @GetMapping("/archived")
    @Operation(summary = "List your archived conversations")
    public PageResponse<ConversationResponse> listArchived(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ConversationResponse> result =
                conversations.listArchived(CurrentCaller.authUserId(), page, size);
        return PageResponse.from(result, r -> r);
    }
}
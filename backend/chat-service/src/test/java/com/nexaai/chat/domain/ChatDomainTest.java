package com.nexaai.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The domain rules that are not about HTTP.
 *
 * <p>The append-only history decision lives here: {@code supersede} plus
 * {@code regeneratedFromId} / {@code editedFromId} is what makes regenerate and edit able to show
 * what came before, which is the only reason those features are worth having.
 */
class ChatDomainTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID CONVERSATION = UUID.randomUUID();

    // ==================================================================
    // Conversation
    // ==================================================================

    @Nested
    @DisplayName("ChatConversation")
    class Conversation {

        @Test
        @DisplayName("starts active, empty and untitled-by-default")
        void defaults() {
            ChatConversation c = ChatConversation.create(OWNER, null, null);

            assertThat(c.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
            assertThat(c.getTitle()).isEqualTo("New conversation");
            assertThat(c.getMessageCount()).isZero();
            assertThat(c.getModel()).isNull();
            assertThat(c.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("ownership is compared as UUIDs")
        void ownership() {
            ChatConversation c = ChatConversation.create(OWNER, null, null);

            assertThat(c.isOwnedBy(OWNER)).isTrue();
            assertThat(c.isOwnedBy(UUID.randomUUID())).isFalse();
            assertThat(c.isOwnedBy(null)).isFalse();
        }

        @Test
        @DisplayName("a derived title applies only while untitled")
        void derivedTitleOnlyWhileUntitled() {
            ChatConversation untitled = ChatConversation.create(OWNER, null, null);
            untitled.applyDerivedTitleIfUntitled("First question");
            assertThat(untitled.getTitle()).isEqualTo("First question");

            // A later message must not clobber the title the conversation now has.
            untitled.applyDerivedTitleIfUntitled("A completely different question");
            assertThat(untitled.getTitle()).isEqualTo("First question");
        }

        @Test
        @DisplayName("a user rename survives a later derived title")
        void renameSurvivesDerivedTitle() {
            ChatConversation c = ChatConversation.create(OWNER, null, null);
            c.rename("Chosen by the user");

            c.applyDerivedTitleIfUntitled("Something else entirely");

            assertThat(c.getTitle()).isEqualTo("Chosen by the user");
        }

        @Test
        @DisplayName("a blank rename is ignored rather than blanking the title")
        void blankRenameIsIgnored() {
            ChatConversation c = ChatConversation.create(OWNER, "Keep me", null);

            c.rename("   ");

            assertThat(c.getTitle()).isEqualTo("Keep me");
        }

        @Test
        @DisplayName("archive and restore are reversible, and idempotent")
        void archiveRestore() {
            ChatConversation c = ChatConversation.create(OWNER, null, null);

            c.archive();
            assertThat(c.getStatus()).isEqualTo(ConversationStatus.ARCHIVED);
            assertThat(c.getStatus().isArchived()).isTrue();

            c.archive();
            assertThat(c.getStatus()).isEqualTo(ConversationStatus.ARCHIVED);

            c.restore();
            assertThat(c.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        }

        @Test
        @DisplayName("lastMessageAt only moves forward")
        void lastMessageAtOnlyMovesForward() {
            // A clock adjustment or an out-of-order write must never make an old conversation
            // jump to the top of the sidebar.
            ChatConversation c = ChatConversation.create(OWNER, null, null);
            java.time.Instant later = java.time.Instant.now().plusSeconds(60);
            java.time.Instant earlier = java.time.Instant.now().minusSeconds(60);

            c.recordMessageAdded(1, later);
            c.recordMessageAdded(1, earlier);

            assertThat(c.getLastMessageAt()).isEqualTo(later);
            assertThat(c.getMessageCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("soft delete is idempotent and keeps the row")
        void softDeleteIsIdempotent() {
            ChatConversation c = ChatConversation.create(OWNER, null, null);

            c.softDelete();
            java.time.Instant first = c.getDeletedAt();
            c.softDelete();

            assertThat(c.isDeleted()).isTrue();
            assertThat(c.getDeletedAt()).isEqualTo(first);
            assertThat(c.getId()).isNotNull();
        }

        @Test
        @DisplayName("toString omits the owner id")
        void toStringIsSafe() {
            ChatConversation c = ChatConversation.create(OWNER, null, null);

            assertThat(c.toString()).doesNotContain(OWNER.toString());
        }
    }

    // ==================================================================
    // Message
    // ==================================================================

    @Nested
    @DisplayName("ChatMessage")
    class Message {

        @Test
        @DisplayName("a user message is COMPLETE")
        void userMessageIsComplete() {
            ChatMessage m = ChatMessage.userMessage(CONVERSATION, OWNER, 1, "Hello");

            assertThat(m.getRole()).isEqualTo(MessageRole.USER);
            assertThat(m.getStatus()).isEqualTo(MessageStatus.COMPLETE);
            assertThat(m.getSequenceNo()).isEqualTo(1);
            assertThat(m.isOwnedBy(OWNER)).isTrue();
            assertThat(m.isCurrent()).isTrue();
        }

        @Test
        @DisplayName("the AI placeholder is PENDING with empty content")
        void placeholderIsPending() {
            ChatMessage m = ChatMessage.assistantPlaceholder(CONVERSATION, OWNER, 2, "gpt-x");

            assertThat(m.getRole()).isEqualTo(MessageRole.ASSISTANT);
            // The whole point: the row exists before generation is attempted, so an interrupted
            // stream leaves evidence rather than a missing reply.
            assertThat(m.getStatus()).isEqualTo(MessageStatus.PENDING);
            assertThat(m.getContent()).isEmpty();
            assertThat(m.getModel()).isEqualTo("gpt-x");
            assertThat(m.isCurrent()).isTrue();
        }

        @Test
        @DisplayName("completing a placeholder records the model that actually ran")
        void completingRecordsTheActualModel() {
            ChatMessage m = ChatMessage.assistantPlaceholder(CONVERSATION, OWNER, 2, "requested");

            m.completeWith("Paris.", "actually-used", 10, 3);

            assertThat(m.getStatus()).isEqualTo(MessageStatus.COMPLETE);
            assertThat(m.getContent()).isEqualTo("Paris.");
            assertThat(m.getModel()).isEqualTo("actually-used");
            assertThat(m.getInputTokens()).isEqualTo(10);
            assertThat(m.getOutputTokens()).isEqualTo(3);
        }

        @Test
        @DisplayName("a COMPLETE message cannot be completed again")
        void completedIsFinal() {
            // Silently rewriting a finished answer makes history lie about what was said.
            ChatMessage placeholder = ChatMessage.assistantPlaceholder(CONVERSATION, OWNER, 2,
                    "m");
            placeholder.completeWith("The real answer", "m", 10, 3);
            assertThat(placeholder.getStatus()).isEqualTo(MessageStatus.COMPLETE);

            assertThatThrownBy(() -> placeholder.completeWith("A different answer", "m", 10, 3))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only a PENDING message can be completed");

            assertThat(placeholder.getContent())
                    .as("the finished answer must be untouched after the refused rewrite")
                    .isEqualTo("The real answer");
        }

        @Test
        @DisplayName("failing records a reason and is not repeated")
        void failingRecordsTheReason() {
            ChatMessage m = ChatMessage.assistantPlaceholder(CONVERSATION, OWNER, 2, null);

            m.fail("provider timed out");
            assertThat(m.getStatus()).isEqualTo(MessageStatus.FAILED);
            assertThat(m.getFailureReason()).isEqualTo("provider timed out");

            // Already terminal: a second failure must not overwrite the first reason.
            m.fail("something else");
            assertThat(m.getFailureReason()).isEqualTo("provider timed out");
        }

        @Test
        @DisplayName("a blank failure reason gets a usable default")
        void blankFailureReasonGetsDefault() {
            ChatMessage m = ChatMessage.assistantPlaceholder(CONVERSATION, OWNER, 2, null);

            m.fail("   ");

            assertThat(m.getFailureReason()).isNotBlank();
        }

        @Test
        @DisplayName("supersede hides a message from history but keeps it in the table")
        void supersedeHidesButKeeps() {
            ChatMessage m = ChatMessage.userMessage(CONVERSATION, OWNER, 1, "Original");

            m.supersede();

            assertThat(m.isCurrent()).isFalse();
            assertThat(m.getContent()).isEqualTo("Original");
            assertThat(m.getSupersededAt()).isNotNull();
            assertThat(m.getId()).isNotNull();
        }

        @Test
        @DisplayName("supersede is idempotent")
        void supersedeIsIdempotent() {
            ChatMessage m = ChatMessage.userMessage(CONVERSATION, OWNER, 1, "Original");

            m.supersede();
            java.time.Instant first = m.getSupersededAt();
            m.supersede();

            assertThat(m.getSupersededAt()).isEqualTo(first);
        }

        @Test
        @DisplayName("a regenerate points at the attempt it replaces")
        void regeneratePointsAtTheOriginal() {
            ChatMessage original = ChatMessage.assistantPlaceholder(CONVERSATION, OWNER, 2, "m");
            ChatMessage replacement = ChatMessage.regeneratedAssistant(CONVERSATION, OWNER, 4,
                    "m", original.getId());

            assertThat(replacement.getRegeneratedFromId()).isEqualTo(original.getId());
            assertThat(replacement.getStatus()).isEqualTo(MessageStatus.PENDING);
        }

        @Test
        @DisplayName("an edit points at the message it replaces")
        void editPointsAtTheOriginal() {
            ChatMessage original = ChatMessage.userMessage(CONVERSATION, OWNER, 1, "Before");
            ChatMessage edited = ChatMessage.editedUserMessage(CONVERSATION, OWNER, 4, "After",
                    original.getId());

            assertThat(edited.getEditedFromId()).isEqualTo(original.getId());
            assertThat(edited.getContent()).isEqualTo("After");
        }

        @Test
        @DisplayName("a SYSTEM message does not accept feedback")
        void systemDoesNotAcceptFeedback() {
            // A system message is injected context, not an answer the user has an opinion about.
            ChatMessage m = ChatMessage.userMessage(CONVERSATION, OWNER, 1, "injected");
            assertThat(m.acceptsFeedback()).isTrue();

            m.supersede();
            assertThat(m.acceptsFeedback()).isFalse();
        }

        @Test
        @DisplayName("toString omits the content")
        void toStringOmitsContent() {
            // Message content is the user's private data; a default toString would put it in
            // every debug log.
            ChatMessage m = ChatMessage.userMessage(CONVERSATION, OWNER, 1,
                    "My private question");

            assertThat(m.toString()).doesNotContain("My private question");
            assertThat(m.toString()).doesNotContain(OWNER.toString());
        }
    }

    // ==================================================================
    // Feedback
    // ==================================================================

    @Nested
    @DisplayName("MessageFeedback")
    class Feedback {

        @Test
        @DisplayName("a blank comment is stored as null, not as an empty string")
        void blankCommentIsNull() {
            MessageFeedback f = MessageFeedback.of(UUID.randomUUID(), OWNER,
                    FeedbackRating.UP, "   ");

            assertThat(f.getComment()).isNull();
            assertThat(f.getRating()).isEqualTo(FeedbackRating.UP);
        }

        @Test
        @DisplayName("updating the rating keeps the original creation time")
        void updateKeepsCreatedAt() {
            MessageFeedback f = MessageFeedback.of(UUID.randomUUID(), OWNER,
                    FeedbackRating.UP, null);
            java.time.Instant created = f.getCreatedAt();

            f.update(FeedbackRating.DOWN, "changed my mind");

            assertThat(f.getRating()).isEqualTo(FeedbackRating.DOWN);
            assertThat(f.getComment()).isEqualTo("changed my mind");
            // When sentiment first arrived is what reporting needs.
            assertThat(f.getCreatedAt()).isEqualTo(created);
        }

        @Test
        @DisplayName("a rating has an opposite")
        void opposite() {
            assertThat(FeedbackRating.UP.opposite()).isEqualTo(FeedbackRating.DOWN);
            assertThat(FeedbackRating.DOWN.opposite()).isEqualTo(FeedbackRating.UP);
        }
    }

    // ==================================================================
    // Title derivation
    // ==================================================================

    @Nested
    @DisplayName("TitleFromFirstMessage")
    class Titles {

        @Test
        @DisplayName("uses the first non-blank line")
        void firstNonBlankLine() {
            assertThat(TitleFromFirstMessage.derive("\n\n  Hello there  \nSecond line"))
                    .isEqualTo("Hello there");
        }

        @Test
        @DisplayName("collapses internal whitespace")
        void collapsesWhitespace() {
            assertThat(TitleFromFirstMessage.derive("a    b\tc")).isEqualTo("a b c");
        }

        @Test
        @DisplayName("truncates on a word boundary and marks the cut")
        void truncatesOnWordBoundary() {
            // Comfortably longer than MAX_LENGTH so truncation is certain.
            String long_ = "alpha bravo charlie delta echo foxtrot golf hotel india juliet "
                    + "kilo lima mike november oscar papa quebec romeo sierra tango";
            String title = TitleFromFirstMessage.derive(long_);

            assertThat(title.length()).isLessThanOrEqualTo(81);
            assertThat(title).endsWith("…");
            assertThat(title).doesNotContain("  ");
        }

        @Test
        @DisplayName("a message at or under the limit is not truncated")
        void shortTitleIsUntouched() {
            String short_ = "alpha bravo charlie";
            assertThat(TitleFromFirstMessage.derive(short_)).isEqualTo(short_);
        }

        @Test
        @DisplayName("falls back for empty input")
        void fallsBack() {
            assertThat(TitleFromFirstMessage.derive(null)).isEqualTo("New conversation");
            assertThat(TitleFromFirstMessage.derive("   ")).isEqualTo("New conversation");
            assertThat(TitleFromFirstMessage.derive("\n\n")).isEqualTo("New conversation");
        }

        @Test
        @DisplayName("a sanitised rename falls back to the current title")
        void sanitiseFallsBack() {
            assertThat(TitleFromFirstMessage.sanitise(null, "Current"))
                    .isEqualTo("Current");
            assertThat(TitleFromFirstMessage.sanitise("  ", "Current"))
                    .isEqualTo("Current");
        }

        @Test
        @DisplayName("a sanitised rename caps at the column width")
        void sanitiseCaps() {
            assertThat(TitleFromFirstMessage.sanitise("x".repeat(500), "Current"))
                    .hasSize(200);
        }
    }
}
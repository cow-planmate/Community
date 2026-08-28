package com.planmate.community.common.persistence;

import com.planmate.community.domain.comment.entity.FeedComment;
import com.planmate.community.domain.comment.entity.Comment;
import com.planmate.community.domain.post.entity.FeedPost;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.reaction.entity.FeedReaction;
import com.planmate.community.domain.reaction.entity.Reaction;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

/** 각 독립 도메인의 시퀀스에서 ID를 발급한다. */
public class DomainSequenceGenerator implements IdentifierGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object entity) {
        String sequence;
        if (entity instanceof FeedPost) {
            sequence = "feed_post_id_seq";
        } else if (entity instanceof FeedComment) {
            sequence = "feed_comment_id_seq";
        } else if (entity instanceof FeedReaction) {
            sequence = "feed_reaction_id_seq";
        } else if (entity instanceof Post) {
            sequence = "community_post_post_id_seq";
        } else if (entity instanceof Comment) {
            sequence = "community_comment_comment_id_seq";
        } else if (entity instanceof Reaction) {
            sequence = "community_reaction_reaction_id_seq";
        } else {
            throw new IllegalArgumentException("지원하지 않는 독립 ID 엔티티: " + entity.getClass());
        }
        return session.createNativeQuery("SELECT nextval('" + sequence + "')", Long.class).getSingleResult();
    }
}

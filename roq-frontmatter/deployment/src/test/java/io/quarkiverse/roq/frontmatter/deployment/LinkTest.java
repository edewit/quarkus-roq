package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.Link.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class LinkTest {

    @Test
    void testLink() {

        JsonObject frontMatter = new JsonObject()
                .put("title", "My First Blog Post")
                .put("year", "2024")
                .put("month", "08")
                .put("day", "27")
                .put("baseFileName", "my-first-blog-post");

        String generatedLink = link("/", ":year/:month/:day/:title", frontMatter);
        assertEquals("2024/08/27/my-first-blog-post", generatedLink);
    }

    @Test
    void testPaginateLink() {
        JsonObject frontMatter = new JsonObject()
                .put("collection", "posts")
                .put("title", "My First Blog Post")
                .put("year", "2024")
                .put("page", 3)
                .put("month", "08")
                .put("day", "27")
                .put("baseFileName", "my-first-blog-post");

        String generatedLink = link("/", DEFAULT_PAGINATE_LINK_TEMPLATE, frontMatter);
        assertEquals("posts/page3", generatedLink);
    }
}

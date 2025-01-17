package io.quarkiverse.roq.frontmatter.deployment;

import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class RoqFrontMatterTest {

    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("application.properties")
                    .addAsResource("site/_data/foo.yml")
                    .addAsResource("site/_includes/foo/bar.html")
                    .addAsResource("site/_includes/view.html")
                    .addAsResource("site/_includes/header.html")
                    .addAsResource("site/_layouts/default.html")
                    .addAsResource("site/_layouts/page.html")
                    .addAsResource("site/_layouts/post.html")
                    .addAsResource("site/index.html")
                    .addAsResource("site/_posts/awesome-post.html")
                    .addAsResource("site/_posts/markdown-post.md")
                    .addAsResource("site/pages/cool-page.html"));

    @Test
    public void testHtmlPost() {
        RestAssured.when().get("/bar/posts/awesome-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("My Cool Post"))
                .body("html.head.base.@href", equalTo("/foo"))
                .body("html.head.meta.findAll { it.@name == 'twitter:url' }.@content", equalTo("https://mywebsite.com/foo/bar"))
                .body("html.body.article.h1", equalTo("A cool blog post"))
                .body("html.body.article.p", equalTo("bar hello"))
                .body("html.body.div.h2", equalTo("My Cool Post"))
                .body("html.body.div.p", equalTo("bar bar bar"));
    }

    @Test
    public void testMdPost() {
        RestAssured.when().get("/bar/posts/markdown-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Markdown Post"))
                .body("html.body.article.h1", equalTo("A post made with markdown"))
                .body("html.body.article.blockquote.p", equalTo("bar"))
                .body("html.body.div.h2", equalTo("Markdown Post"))
                .body("html.body.div.p", equalTo("bar bar bar"));
    }

    @Test
    public void testPage() {
        RestAssured.when().get("/bar/my-cool-page").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("My Cool Page"))
                .body("html.body.article.h1", equalTo("Hello World"))
                .body("html.body.article.p", equalTo("bar"))
                .body("html.body.div.h2", equalTo("My Cool Page"))
                .body("html.body.div.p", equalTo("bar bar bar"));
    }

    @Test
    public void testIndex() {
        RestAssured.when().get("/bar").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Hello, world! I'm Roq"))
                .body("html.body.div.h1[0]", containsString("posts/awesome-post"))
                .body("html.body.div.h1[1]", containsString("posts/markdown-post"))
                .body("html.body.div.h2", equalTo("Hello, world! I'm Roq"))
                .body("html.body.div.p", equalTo("bar bar bar"));
    }

}

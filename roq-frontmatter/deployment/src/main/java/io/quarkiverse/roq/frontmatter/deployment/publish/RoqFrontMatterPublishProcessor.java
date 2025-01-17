package io.quarkiverse.roq.frontmatter.deployment.publish;

import static io.quarkiverse.roq.frontmatter.deployment.Link.DEFAULT_PAGINATE_LINK_TEMPLATE;
import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.PAGINATE_KEY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.Link;
import io.quarkiverse.roq.frontmatter.deployment.Paginate;
import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDocumentTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterPaginateTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.frontmatter.runtime.model.Paginator;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.vertx.core.json.JsonObject;

class RoqFrontMatterPublishProcessor {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterPublishProcessor.class);

    @BuildStep
    public void publishCollections(
            List<RoqFrontMatterDocumentTemplateBuildItem> documentTemplates,
            BuildProducer<RoqFrontMatterPublishDocumentPageBuildItem> publishDocuments) {
        for (RoqFrontMatterDocumentTemplateBuildItem documentTemplate : documentTemplates) {
            publishDocuments.produce(new RoqFrontMatterPublishDocumentPageBuildItem(documentTemplate.url(),
                    documentTemplate.item().info(), documentTemplate.collection(), documentTemplate.data()));
        }
    }

    @BuildStep
    public void paginatePublish(RoqSiteConfig config,
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            BuildProducer<RoqFrontMatterPublishPageBuildItem> pagesProducer,
            List<RoqFrontMatterPaginateTemplateBuildItem> paginationList,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents,
            List<RoqFrontMatterPublishDerivedCollectionBuildItem> derivedCollections) {
        if (paginationList.isEmpty() || rootUrlItem == null) {
            return;
        }
        final RootUrl rootUrl = rootUrlItem.rootUrl();
        final Map<String, AtomicInteger> sizeByCollection = new HashMap<>();

        for (RoqFrontMatterPublishDocumentPageBuildItem document : documents) {
            sizeByCollection.computeIfAbsent(document.collection(), k -> new AtomicInteger()).incrementAndGet();
        }

        for (RoqFrontMatterPublishDerivedCollectionBuildItem derivedCollection : derivedCollections) {
            sizeByCollection.computeIfAbsent(derivedCollection.collection(), k -> new AtomicInteger())
                    .addAndGet(derivedCollection.documentIds().size());
        }

        for (RoqFrontMatterPaginateTemplateBuildItem pagination : paginationList) {
            final RoqFrontMatterRawTemplateBuildItem item = pagination.item();
            final JsonObject data = pagination.data();
            final String link = pagination.link();
            Paginate paginate = readPaginate(item.id(), data, pagination.defaultPaginatedCollection());
            AtomicInteger collectionSize = sizeByCollection.get(paginate.collection());
            if (collectionSize == null) {
                throw new ConfigurationException(
                        "Paginate collection not found '" + paginate.collection() + "' in " + item.id());
            }
            final int total = collectionSize.get();
            if (paginate.size() <= 0) {
                throw new ConfigurationException("Page size must be greater than zero.");
            }
            int countPages = (total + paginate.size() - 1) / paginate.size();

            List<PageToPublish> paginatedPages = new ArrayList<>();
            final String linkTemplate = paginate.link() != null ? paginate.link() : DEFAULT_PAGINATE_LINK_TEMPLATE;
            for (int i = 1; i <= countPages; i++) {
                final String paginatedLink = i == 1 ? link
                        : Link.paginateLink(config.rootPath(), linkTemplate,
                                new Link.PaginateLinkData(item.info().baseFileName(), item.info().date(),
                                        paginate.collection(), Integer.toString(i), data));
                PageInfo info = item.info();
                if (i > 1) {
                    info = info.changeId(paginatedLink);
                }
                paginatedPages.add(new PageToPublish(paginatedLink, info, data));
            }

            for (int i = 1; i <= countPages; i++) {
                Integer prev = i > 1 ? i - 1 : null;
                Integer next = i <= countPages - 1 ? i + 1 : null;
                PageToPublish currentPage = paginatedPages.get(i - 1);
                PageToPublish previousPage;
                PageToPublish nextPage;
                RoqUrl previousUrl = null;
                RoqUrl nextUrl = null;
                if (prev != null) {
                    previousPage = paginatedPages.get(prev - 1);
                    previousUrl = rootUrl.resolve(previousPage.link());
                }
                if (next != null) {
                    nextPage = paginatedPages.get(next - 1);
                    nextUrl = rootUrl.resolve(nextPage.link());
                }
                Paginator paginator = new Paginator(paginate.collection(), total, paginate.size(), countPages, i, prev,
                        previousUrl, next, nextUrl);
                pagesProducer.produce(new RoqFrontMatterPublishPageBuildItem(currentPage.link(), currentPage.info(),
                        currentPage.data(), paginator));
            }
        }

    }

    private static Paginate readPaginate(String name, JsonObject data, String defaultCollection) {
        final Object value = data.getValue(PAGINATE_KEY);
        if (value instanceof JsonObject paginate) {
            final String collection = paginate.getString("collection", defaultCollection);
            if (collection == null) {
                throw new ConfigurationException("Invalid pagination configuration in " + name);
            }
            return new Paginate(paginate.getInteger("size", 5), paginate.getString("link", DEFAULT_PAGINATE_LINK_TEMPLATE),
                    collection);
        }
        if (value instanceof String collection) {
            return new Paginate(5, DEFAULT_PAGINATE_LINK_TEMPLATE, collection);
        }
        if (value instanceof Boolean paginate && paginate) {
            if (defaultCollection == null) {
                throw new ConfigurationException("Invalid pagination configuration in " + name);
            }
            return new Paginate(5, DEFAULT_PAGINATE_LINK_TEMPLATE, defaultCollection);
        }
        throw new ConfigurationException("Invalid pagination configuration in " + name);
    }

    private record PageToPublish(String link, PageInfo info, JsonObject data) {
    }

}

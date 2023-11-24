package com.eulerity.hackathon.imagefinder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class WebCrawler {
  private Set<String> links;
  private Set<String> imageUrls;

  // Initialize ExecutorService with CPU Cores - 1
  private ExecutorService executorService;
  private static final long REQUEST_DELAY_MS = 1000; // 1-second delay

  public WebCrawler() {
    this.links = new HashSet<>();
    this.imageUrls = new HashSet<>();
    int numberOfThreads = Runtime.getRuntime().availableProcessors() - 1;
    numberOfThreads = Math.max(numberOfThreads, 1); // Ensure at least one thread
    this.executorService = Executors.newFixedThreadPool(numberOfThreads);
  }

  public Document fetchHTML(String url) throws IOException {
    return Jsoup.connect(url).get();
  }

  public synchronized void crawl(String url) {
    if (!links.contains(url)) {
      try {
        Thread.sleep(REQUEST_DELAY_MS); // Rate limiting
        Document document = fetchHTML(url);
        links.add(url);

        // Extract images
        Elements images = document.select("img[src]");
        for (Element img : images) {
          imageUrls.add(img.attr("abs:src"));
        }

        extractImages(document);
        extractFavicons(document);

        // Crawl other links on the page
        Elements linksOnPage = document.select("a[href]");
        for (Element page : linksOnPage) {
          String subUrl = page.attr("abs:href");
          if (subUrl.startsWith(url) && !links.contains(subUrl)) {
            executorService.submit(() -> crawl(subUrl));
          }
        }
      } catch (IOException | InterruptedException e) {
        System.err.println("For '" + url + "': " + e.getMessage());
        Thread.currentThread().interrupt(); // Restore interrupted status
      }
    }
  }

  public synchronized void extractImages(Document document) {
    Elements images = document.select("img[src]");
    for (Element img : images) {
      imageUrls.add(img.attr("abs:src"));
    }
  }

  public synchronized void extractFavicons(Document document) {
    Elements faviconLinks = document.select("head link[rel=icon], head link[rel='shortcut icon']");
    for (Element link : faviconLinks) {
      String faviconUrl = link.attr("abs:href");
      imageUrls.add("Favicon: " + faviconUrl); // Prefix to identify favicons
    }
  }

  // Placeholder for image classification
  public void classifyImages() {
    // Implement image classification logic here
  }

  public Map<String, List<String>> getImageUrlsStructured() {
    Map<String, List<String>> categorizedImages = new HashMap<>();
    categorizedImages.put("images", new ArrayList<>());
    categorizedImages.put("favicons", new ArrayList<>());

    for (String url : imageUrls) {
      if (url.startsWith("Favicon: ")) {
        categorizedImages.get("favicons").add(url.substring(8)); // Remove prefix
      } else {
        categorizedImages.get("images").add(url);
      }
    }

    return categorizedImages;
  }

  public void startCrawling(String startUrl) {
    crawl(startUrl);
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(60, TimeUnit.MINUTES)) {
        executorService.shutdownNow();
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
          System.err.println("Executor service did not terminate");
      }
    } catch (InterruptedException e) {
      System.err.println("Interrupted while waiting for executor service to terminate");
      Thread.currentThread().interrupt();
    }
  }
}

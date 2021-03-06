package com.ximoneighteen.android.rssreader.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.util.Collections;

import org.xmlpull.v1.XmlPullParser;

import android.os.NetworkOnMainThreadException;
import android.util.Xml;

import com.ximoneighteen.android.rssreader.model.Article;
import com.ximoneighteen.android.rssreader.model.Feed;

public class RssDownloader implements Runnable {

	private static final String ITEM = "item";
	private static final String TITLE = "title";
	private static final String DESCRIPTION = "description";
	private static final String LINK = "link";
	private static final String GUID = "guid";
	private static final String PUB_DATE = "pubDate";
	private static final String CHANNEL = "channel";

	private final Feed feed;

	private HttpHelper httpHelper = null;

	public RssDownloader(final Feed feed) {
		this.feed = feed;
		this.httpHelper = new HttpHelper();
	}

	public RssDownloader(final HttpHelper httpHelper, final Feed feed) {
		this.feed = feed;
		this.httpHelper = httpHelper;
	}

	@Override
	public void run() {
		InputStream is = null;
		try {
			is = httpHelper.getInputStreamFromURL(feed.getUrl());
			if (is != null) {
				parseXML(is);
			}
		} catch (ProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NetworkOnMainThreadException e) {
			// shouldn't happen! developer error!
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void parseXML(final InputStream is) {
		XmlPullParser parser = Xml.newPullParser();
		try {
			// auto-detect the encoding from the stream
			parser.setInput(is, null);
			int eventType = parser.getEventType();
			boolean done = false;

			boolean inItem = false;
			String title = null;
			String description = null;
			String link = null;
			String date = null;
			String guid = null;

			while (eventType != XmlPullParser.END_DOCUMENT && !done) {
				String name = null;
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;
				case XmlPullParser.START_TAG:
					name = parser.getName();
					if (name.equalsIgnoreCase(ITEM)) {
						inItem = true;
					} else if (inItem) {
						if (name.equalsIgnoreCase(LINK)) {
							link = parser.nextText();
						} else if (name.equalsIgnoreCase(DESCRIPTION)) {
							description = parser.nextText();
						} else if (name.equalsIgnoreCase(PUB_DATE)) {
							date = parser.nextText();
						} else if (name.equalsIgnoreCase(TITLE)) {
							title = parser.nextText();
						} else if (name.equalsIgnoreCase(GUID)) {
							guid = parser.nextText();
						}
					}
					break;
				case XmlPullParser.END_TAG:
					name = parser.getName();
					if (name.equalsIgnoreCase(ITEM) && inItem) {
						Article article = new Article(title, description, link, guid, date);
						article.setFeedId(feed.getId());
						mergeArticleIntoFeed(article);
					} else if (name.equalsIgnoreCase(CHANNEL)) {
						done = true;
					}
					break;
				}
				eventType = parser.next();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void mergeArticleIntoFeed(final Article article) {
		if (feed.getArticles() == null) {
			feed.setArticles(Collections.singletonList(article));
		} else {
			Article existingArticle = feed.findArticleByGuid(article.getGuid());
			if (existingArticle != null) {
				if (article.getDate().after(existingArticle.getDate())) {
					feed.getArticles().remove(existingArticle);
					feed.getArticles().add(article);
				}
			} else {
				feed.getArticles().add(article);
			}
		}
	}

}

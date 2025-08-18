package com.jalios.jcmsplugin.jbook;

import com.jalios.jcms.Category;
import com.jalios.jcms.Channel;

public class JBookManager {
	public static final String VID_TOPIC_ROOT = "$id.jcmsplugin.jbook.catalog.topic-root";
	private static final JBookManager SINGLETON = new JBookManager();

// --------------------------------------------------------
// Singleton & init
// --------------------------------------------------------
	private JBookManager() {
// Empty
	}

	public static JBookManager getInstance() {
		return SINGLETON;
	}

	/**
	 * Returns the root of the topics.
	 * 
	 * @return the root of the topics.
	 */
	public Category getTopicRoot() {
		return Channel.getChannel().getCategory(VID_TOPIC_ROOT);
	}
}
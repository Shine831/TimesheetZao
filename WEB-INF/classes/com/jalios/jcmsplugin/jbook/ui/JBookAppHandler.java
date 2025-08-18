package com.jalios.jcmsplugin.jbook.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import com.jalios.jcms.Category;
import com.jalios.jcms.JcmsUtil;
import com.jalios.jcms.Publication;
import com.jalios.jcms.QueryResultSet;
import com.jalios.jcms.comparator.ComparatorManager;
import com.jalios.jcms.handler.QueryHandler;
import com.jalios.jcms.uicomponent.BreadcrumbItem;
import com.jalios.jcms.uicomponent.DataAttribute;
import com.jalios.jcmsplugin.jbook.JBookManager;
import com.jalios.util.Util;

import generated.Book;

//...

public class JBookAppHandler extends QueryHandler {

	protected Category topic;
	protected boolean showTopics;

	protected JBookManager mgr = JBookManager.getInstance();

	@Override
	public void init() {
		initTopic();
		initQuery();
	}

	private void initTopic() {
		Set<Category> catSet = getCategorySet("cids");

		if (Util.isEmpty(catSet)) {
			setCids(JcmsUtil.getId(mgr.getTopicRoot()));
			catSet = getCategorySet("cids");
		}

		topic = Util.getFirst(catSet);
		showTopics = Util.isEmpty(getText());
	}

	private void initQuery() {
		setTypes("generated.Book");
		setSort("title");
		setExactCat(showTopics);
	}

// ...

	protected QueryResultSet resultSet;

	@Override
	public QueryResultSet getResultSet() {
		if (resultSet == null) {
			resultSet = super.getResultSet();
		}
		return resultSet;
	}

	public SortedSet<Publication> getSortedResultSet() {
		Comparator comparator = ComparatorManager.getComparator(Publication.class, getSort());
		return getResultSet().getAsSortedSet(comparator);
	}

	public boolean showNoResult() {
		return Util.isEmpty(getResultSet());
	}

	public String getAppUrl() {
		return "plugins/JBookPlugin/jsp/app/jbook.jsp";
	}

	public String getAppUrl(Category cat) {
		StringBuilder sb = new StringBuilder(getAppUrl());
		sb.append("?");
		if (Util.notEmpty(getText())) {
			sb.append("text=" + getText() + "&amp;");
		}

		if (cat != null) {
			sb.append("cids=" + cat.getId());
		}

		return sb.toString();
	}

	public boolean showAddBook() {
		if (!isLogged) {
			return false;
		}
		return loggedMember.canPublishSome(Book.class);
	}

	public String getAddBookUrl() {
		String params = "";
		Category topic = getSelectedTopic();
		if (topic != null) {
			params = "?cids=" + topic.getId();
		}
		return "types/Book/editBookModal.jsp" + params;
	}

	protected Book book;

	public boolean showCatalog() {
		return !showBook();
	}

	public boolean showBook() {
		return getSelectedBook() != null;
	}

	public Book getSelectedBook() {
		return book;
	}

	public void setBook(String id) {
		book = channel.getData(Book.class, id);
	}

	public String getEditBookUrl() {
		if (book == null) {
			return null;
		}
		return "types/Book/editBookModal.jsp?id=" + book.getId();
	}

	public Category getSelectedTopic() {
		return topic;
	}

	public boolean showTopics() {
		return showTopics;
	}

	public Set<Category> getTopicSet() {
		Category topic = getSelectedTopic();
		if (topic != null) {
			return topic.getChildrenSet();
		}
		Category root = mgr.getTopicRoot();
		if (root == null) {
			return null;
		}
		return root.getChildrenSet();
	}

	// ------------------------------------------------------------------
	// Breadcrumbs
	// ------------------------------------------------------------------

	public List<BreadcrumbItem> getBreadcrumbItems() {
		List<BreadcrumbItem> items = new ArrayList<>();

		items.add(new BreadcrumbItem().label(glp("jcmsplugin.jbook.app.catalog.root")).url(getAppUrl())
				.attributes(new DataAttribute().addData("data-jalios-action", "ajax-refresh")));

		List<Category> breadcrumb = getBreadCrumbCatList();
		if (Util.isEmpty(breadcrumb)) {
			return items;
		}

		for (Category cat : breadcrumb) {
			items.add(new BreadcrumbItem().label(cat.getName(userLang)).url(getAppUrl(cat))
					.attributes(new DataAttribute().addData("data-jalios-action", "ajax-refresh")));
		}

		return items;
	}

	private List<Category> getBreadCrumbCatList() {
		Category currentCat = getSelectedTopic();
		if (currentCat == null || currentCat == topic) {
			return Collections.emptyList();
		}

		List<Category> list = currentCat.getAncestorList(topic, false);
		Collections.reverse(list);
		list.add(currentCat);

		return list;
	}

}
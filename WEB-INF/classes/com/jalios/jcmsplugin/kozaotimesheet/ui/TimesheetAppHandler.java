package com.jalios.jcmsplugin.kozaotimesheet.ui;

import com.jalios.jcms.handler.QueryHandler;
import com.jalios.jcms.Channel;
import com.jalios.jcms.Group;

import generated.Project;
import generated.ProjectTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;

public class TimesheetAppHandler extends QueryHandler {

	public enum View {
		MY_TIMESHEETS, MANAGER_VIEW
	}

	private int currentWeekNumber;
	private View currentView = View.MY_TIMESHEETS; // default

	// key declared in plugin.prop / languages.
	public static final String ACL_MANAGER_VIEW = "jcmsplugin.kozaotimesheet.app.manager-view";

	public TimesheetAppHandler() {
		super();
	}

	@Override
	public void init() {
		super.init();

		// calcul semaine courante
		if (loggedMember != null) {
			Calendar cal = GregorianCalendar.getInstance();
			int year = cal.get(Calendar.YEAR);
			int week = cal.get(Calendar.WEEK_OF_YEAR);
			this.currentWeekNumber = (year * 100) + week;
		}

		// récupération sécurisée du paramètre "view"
		String viewParam = null;
		try {
			if (getRequest() != null) {
				viewParam = getRequest().getParameter("view");
			}
		} catch (Throwable ignore) {
			viewParam = null;
		}

		if ("manager".equals(viewParam)) {
			// si la vue manager est demandée, vérifier les droits
			if (canAccessManagerView()) {
				this.currentView = View.MANAGER_VIEW;
			} else {
				try {
					if (getRequest() != null) {
						getRequest().setAttribute("accessDenied", Boolean.TRUE);
					}
				} catch (Throwable ignore) {
				}
				this.currentView = View.MY_TIMESHEETS;
			}
		} else {
			this.currentView = View.MY_TIMESHEETS;
		}

		// expose attributs pour JSPs
		try {
			if (getRequest() != null) {
				String viewStr = (this.currentView == View.MANAGER_VIEW) ? "manager" : "employee";

				String baseAppUrl = getAppUrl();
				String employeeUrl = baseAppUrl + (baseAppUrl.indexOf('?') >= 0 ? "&view=employee" : "?view=employee");
				String managerUrl = baseAppUrl + (baseAppUrl.indexOf('?') >= 0 ? "&view=manager" : "?view=manager");

				getRequest().setAttribute("view", viewStr);
				getRequest().setAttribute("handlerCurrentView", viewStr); // backward compat
				getRequest().setAttribute("employeeUrl", employeeUrl);
				getRequest().setAttribute("managerUrl", managerUrl);

				boolean isManager = false;
				try {
					isManager = canAccessManagerView();
				} catch (Throwable ignore) {
				}
				getRequest().setAttribute("isManager", Boolean.valueOf(isManager));
			}
		} catch (Throwable ignore) {
			// never fail init because of request attribute setting
		}

		// --- Fournir allowedProjects / allowedTasks (typié) pour la modale ---
		try {
			if (getRequest() != null) {
				List<Project> allowedProjects = new ArrayList<Project>();
				List<ProjectTask> allowedTasks = new ArrayList<ProjectTask>();

				Channel ch = Channel.getChannel();
				if (ch != null) {
					// récupération typée via API Channel (retourne Set)
					try {
						Set<Project> projSet = ch.getPublicationSet(Project.class, this.loggedMember);
						if (projSet != null)
							allowedProjects.addAll(projSet);
					} catch (Throwable ignore) {
						// fallback: ignore if method not present in this JCMS version
					}

					try {
						Set<ProjectTask> taskSet = ch.getPublicationSet(ProjectTask.class, this.loggedMember);
						if (taskSet != null)
							allowedTasks.addAll(taskSet);
					} catch (Throwable ignore) {
						// fallback
					}
				}

				getRequest().setAttribute("allowedProjects", allowedProjects);
				getRequest().setAttribute("allowedTasks", allowedTasks);
			}
		} catch (Throwable ignore) {
			// do not fail init
		}
	}

	@Override
	public boolean processAction() throws IOException {
		// Pas de traitement spécifique ici
		return false;
	}

	public String getAppUrl() {
		// Keep same style as existing code (no leading slash) for compatibility with
		// sidebar logic
		return "plugins/KozaoTimesheetPlugin/jsp/app/timesheetApp.jsp";
	}

	public String getManagerViewUrl() {
		return getAppUrl() + "?view=manager";
	}

	public String getAppTitle() {
		if (this.currentView == View.MANAGER_VIEW) {
			return glp("jcmsplugin.kozaotimesheet.app.view.manager");
		}
		return glp("jcmsplugin.kozaotimesheet.app.view.mytimesheets");
	}

	public int getCurrentWeekNumber() {
		return this.currentWeekNumber;
	}

	public View getCurrentView() {
		return this.currentView;
	}

	/**
	 * Vérifie l'ACL principal et, si indisponible, utilise un fallback par
	 * appartenance groupe.
	 */
	public boolean canAccessManagerView() {
		try {
			boolean res = checkAccess(ACL_MANAGER_VIEW);
			return res;
		} catch (Throwable t) {
			// fallback below
		}

		try {
			if (isMemberOfGroup("Gestionnaires"))
				return true;
			if (isMemberOfGroup("gestionnaires"))
				return true;
			if (isMemberOfGroup("ROLE_GESTIONNAIRES"))
				return true;
			if (isMemberOfGroup("Administrateurs"))
				return true;
		} catch (Throwable ignore) {
		}

		return false;
	}

	/**
	 * Méthode robuste d'appartenance à un groupe (compatible Group[]).
	 */
	public boolean isMemberOfGroup(String groupName) {
		if (this.loggedMember == null)
			return false;
		try {
			Group[] groups = this.loggedMember.getGroups();
			if (groups != null) {
				for (Group g : groups) {
					try {
						if (g != null && groupName.equalsIgnoreCase(g.getName()))
							return true;
					} catch (Throwable ignore) {
					}
				}
			}
		} catch (Throwable ignore) {
		}
		return false;
	}

	/**
	 * Retourne true si membre courant considéré comme gestionnaire (alias).
	 */
	public boolean isManager() {
		return canAccessManagerView();
	}
}

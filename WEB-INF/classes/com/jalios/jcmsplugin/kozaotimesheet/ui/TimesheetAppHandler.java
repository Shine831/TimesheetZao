package com.jalios.jcmsplugin.kozaotimesheet.ui;

import com.jalios.jcms.handler.QueryHandler;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.lang.reflect.Method;
import java.util.Collection;

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

		// Récupération sûre du paramètre "view" via la requête HTTP
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
				// défense en profondeur : ne pas lancer d'API qui pourrait ne pas exister.
				// expose un attribut pour que JSP affiche un message d'accès refusé.
				try {
					if (getRequest() != null) {
						getRequest().setAttribute("accessDenied", Boolean.TRUE);
					}
				} catch (Throwable ignore) {
				}
				// on reste en vue employé (par défaut)
				this.currentView = View.MY_TIMESHEETS;
			}
		} else {
			this.currentView = View.MY_TIMESHEETS;
		}

		// expose current view (utile aux JSPf)
		try {
			if (getRequest() != null) {
				getRequest().setAttribute("handlerCurrentView",
						this.currentView == View.MANAGER_VIEW ? "manager" : "employee");
			}
		} catch (Throwable ignore) {
		}
		// expose des attributs pratiques pour les JSPf (compatibilité et simplicité)
		try {
			if (getRequest() != null) {
				String viewStr = (this.currentView == View.MANAGER_VIEW) ? "manager" : "employee";

				// URLs robustes (utilisent getAppUrl())
				String baseAppUrl = getAppUrl();
				String employeeUrl = baseAppUrl + (baseAppUrl.indexOf('?') >= 0 ? "&view=employee" : "?view=employee");
				String managerUrl = baseAppUrl + (baseAppUrl.indexOf('?') >= 0 ? "&view=manager" : "?view=manager");

				getRequest().setAttribute("view", viewStr);
				getRequest().setAttribute("handlerCurrentView", viewStr); // backward compat
				getRequest().setAttribute("employeeUrl", employeeUrl);
				getRequest().setAttribute("managerUrl", managerUrl);

				// indique si l'utilisateur est autorisé (utilise la méthode existante
				// canAccessManagerView)
				boolean isManager = false;
				try {
					isManager = canAccessManagerView();
				} catch (Throwable ignore) {
				}
				getRequest().setAttribute("isManager", Boolean.valueOf(isManager));
			}
		} catch (Throwable ignore) {
			// Never fail init because of request attribute setting
		}

	}

	@Override
	public boolean processAction() throws IOException {
		// La logique de sauvegarde sera gérée par un Edit...Handler dédié plus tard.
		return false;
	}

	public String getAppUrl() {
		// return with leading slash to make links robust
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
		// 1) try native checkAccess if available
		try {
			// checkAccess is part of some JCMS API (may throw if not present)
			boolean res = checkAccess(ACL_MANAGER_VIEW);
			return res;
		} catch (Throwable t) {
			// fallback below
		}

		// 2) fallback: check membership in common group names
		try {
			if (isMemberOfGroup("Gestionnaires"))
				return true;
			if (isMemberOfGroup("gestionnaires"))
				return true;
			if (isMemberOfGroup("ROLE_GESTIONNAIRES"))
				return true;
			if (isMemberOfGroup("Administrateurs"))
				return true; // optional: admins
		} catch (Throwable ignore) {
		}

		return false;
	}

	/**
	 * Méthode robuste d'appartenance à un groupe (réflexion).
	 */
	public boolean isMemberOfGroup(String groupName) {
		try {
			if (this.loggedMember == null)
				return false;

			// 1) try loggedMember.isMemberOf(String)
			try {
				Method isMemberOf = this.loggedMember.getClass().getMethod("isMemberOf", String.class);
				Object res = isMemberOf.invoke(this.loggedMember, groupName);
				if (res instanceof Boolean)
					return (Boolean) res;
			} catch (NoSuchMethodException nsme) {
				// ignore
			} catch (Throwable t) {
				// ignore
			}

			// 2) try getGroups()
			try {
				Method getGroups = this.loggedMember.getClass().getMethod("getGroups");
				Object groupsObj = getGroups.invoke(this.loggedMember);
				if (groupsObj instanceof Collection) {
					@SuppressWarnings("unchecked")
					Collection<Object> groups = (Collection<Object>) groupsObj;
					for (Object g : groups) {
						if (g == null)
							continue;
						// try getName/getId/getLabel
						try {
							Method getName = g.getClass().getMethod("getName");
							Object name = getName.invoke(g);
							if (groupName.equalsIgnoreCase(String.valueOf(name)))
								return true;
						} catch (Throwable ignore) {
						}
						try {
							Method getId = g.getClass().getMethod("getId");
							Object id = getId.invoke(g);
							if (groupName.equalsIgnoreCase(String.valueOf(id)))
								return true;
						} catch (Throwable ignore) {
						}
						try {
							Method getLabel = g.getClass().getMethod("getLabel");
							Object label = getLabel.invoke(g);
							if (groupName.equalsIgnoreCase(String.valueOf(label)))
								return true;
						} catch (Throwable ignore) {
						}
						// fallback toString()
						if (groupName.equalsIgnoreCase(String.valueOf(g)))
							return true;
					}
				} else if (groupsObj != null && groupsObj.getClass().isArray()) {
					Object[] arr = (Object[]) groupsObj;
					for (Object g : arr) {
						if (g == null)
							continue;
						try {
							Method getName = g.getClass().getMethod("getName");
							Object name = getName.invoke(g);
							if (groupName.equalsIgnoreCase(String.valueOf(name)))
								return true;
						} catch (Throwable ignore) {
						}
					}
				}
			} catch (NoSuchMethodException nsme) {
				// ignore
			} catch (Throwable t) {
				// ignore
			}

			// 3) try getGroupIds()
			try {
				Method getGroupIds = this.loggedMember.getClass().getMethod("getGroupIds");
				Object idsObj = getGroupIds.invoke(this.loggedMember);
				if (idsObj instanceof Collection) {
					@SuppressWarnings("unchecked")
					Collection<Object> ids = (Collection<Object>) idsObj;
					for (Object id : ids) {
						if (groupName.equalsIgnoreCase(String.valueOf(id)))
							return true;
					}
				} else if (idsObj != null && idsObj.getClass().isArray()) {
					Object[] idsArr = (Object[]) idsObj;
					for (Object id : idsArr) {
						if (groupName.equalsIgnoreCase(String.valueOf(id)))
							return true;
					}
				}
			} catch (Throwable ignore) {
			}

		} catch (Throwable t) {
			// safe fallback
		}
		return false;
	}

	// --- add this method in TimesheetAppHandler.java ---

	/**
	 * Retourne true si le membre courant est considéré comme "gestionnaire". 1)
	 * Essaye checkAccess(ACL_MANAGER_VIEW) si disponible (préférence à l'ACL). 2)
	 * Fallback : vérifie l'appartenance à des noms techniques/usages courants.
	 */
	public boolean isManager() {
		// constant for resource key (assure-toi que cette constante existe si tu
		// l'utilises)
		final String ACL_KEY = "jcmsplugin.kozaotimesheet.app.manager-view";

		try {
			// 1) try native ACL check (may throw if not available)
			try {
				boolean res = checkAccess(ACL_KEY);
				return res;
			} catch (Throwable t) {
				// ignore and fallback to group checks
			}

			// 2) fallback: check membership in common group names
			if (isMemberOfGroup("Gestionnaires"))
				return true;
			if (isMemberOfGroup("gestionnaires"))
				return true;
			if (isMemberOfGroup("ROLE_GESTIONNAIRES"))
				return true;
			if (isMemberOfGroup("Administrateurs"))
				return true; // optionally allow admins

		} catch (Throwable t) {
			// safe fallback
		}
		return false;
	}
}
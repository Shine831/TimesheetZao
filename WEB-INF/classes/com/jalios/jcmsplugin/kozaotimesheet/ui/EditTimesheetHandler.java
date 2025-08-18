package com.jalios.jcmsplugin.kozaotimesheet.ui;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.jalios.jcms.handler.EditDataHandler;
import com.jalios.jcms.Channel;
import com.jalios.jcms.Data;
import com.jalios.jcms.Member;

import generated.Timesheet;
import generated.TimeEntry;

/**
 * Handler "robuste" pour la création / édition d'une Timesheet depuis la modal.
 *
 * Notes : - Beaucoup de méthodes sont appelées via réflexion pour supporter
 * plusieurs versions de JCMS/génération. - Adapte les noms de setter si ta
 * génération a des noms différents (voir les TODO dans le code).
 */
public class EditTimesheetHandler extends EditDataHandler {

	private static final long serialVersionUID = 1L;

	public EditTimesheetHandler() {
		super();
	}

	// --- compatibilité API : retourne la classe de la publication manipulée
	// (Timesheet)
	public Class<? extends Data> getPublicationClass() {
		return Timesheet.class;
	}

	// Certains JCMS demandent getDataClass() au lieu de getPublicationClass()
	public Class<? extends Data> getDataClass() {
		return getPublicationClass();
	}

	/**
	 * Pré-remplissage à la création : employee = loggedMember, weekNumber =
	 * année*100 + semaine (méthode appelée avant l'affichage du formulaire)
	 */
	public void setupNewPublication() {
		try {
			Object pub = getPublication();
			Timesheet timesheet = null;
			if (pub == null) {
				// si l'API permet setPublication(Timesheet) on l'utilise via réflexion
				try {
					timesheet = new Timesheet();
					Method setPub = this.getClass().getMethod("setPublication", Timesheet.class);
					setPub.invoke(this, timesheet);
				} catch (NoSuchMethodException nsme) {
					// si setPublication n'existe pas, on tente d'accéder via méthode du parent si
					// possible
					// (dans certains contextes la publication est créée automatiquement)
				}
			} else if (pub instanceof Timesheet) {
				timesheet = (Timesheet) pub;
			}

			if (timesheet != null) {
				// employee
				Member lm = getLoggedMember();
				if (lm != null) {
					// setter possible : setEmployee(Member) ou setEmployee(String)
					try {
						Method m = timesheet.getClass().getMethod("setEmployee", Member.class);
						m.invoke(timesheet, lm);
					} catch (Exception e1) {
						try {
							Method m2 = timesheet.getClass().getMethod("setEmployee", String.class);
							m2.invoke(timesheet, lm.getId());
						} catch (Exception ignore) {
						}
					}
				}

				// semaine courante
				try {
					java.util.Calendar cal = java.util.GregorianCalendar.getInstance();
					int year = cal.get(java.util.Calendar.YEAR);
					int week = cal.get(java.util.Calendar.WEEK_OF_YEAR);
					int weekNumber = year * 100 + week;
					try {
						Method setWeek = timesheet.getClass().getMethod("setWeekNumber", int.class);
						setWeek.invoke(timesheet, weekNumber);
					} catch (Exception e) {
						// fallback : essayer Integer
						try {
							Method setWeek = timesheet.getClass().getMethod("setWeekNumber", Integer.class);
							setWeek.invoke(timesheet, Integer.valueOf(weekNumber));
						} catch (Exception ignore) {
						}
					}
				} catch (Throwable ignore) {
				}
			}
		} catch (Throwable ignore) {
			// ne pas bloquer l'initialisation du formulaire pour un problème mineur
		}
	}

	/**
	 * Nous interceptons la sauvegarde pour parser les time entries fournis par le
	 * formulaire et les attacher à la Timesheet avant sauve.
	 *
	 * La méthode tente d'appeler super.saveData() si elle existe, sinon
	 * super.processAction().
	 */
	public boolean processAction() throws IOException {
		// On prépare la publication (habituellement le parent a déjà préparé)
		try {
			// on récupère la timesheet en cours (création ou édition)
			Object pub = getPublication();
			Timesheet timesheet = null;
			if (pub instanceof Timesheet) {
				timesheet = (Timesheet) pub;
			}

			// Si la requête contient nos paramètres de time entries -> on les parse
			javax.servlet.http.HttpServletRequest req = getRequest();
			if (req != null) {
				String[] projectIds = req.getParameterValues("projectId[]");
				String[] taskIds = req.getParameterValues("taskId[]");
				String[] monday = req.getParameterValues("monday[]");
				String[] tuesday = req.getParameterValues("tuesday[]");
				String[] wednesday = req.getParameterValues("wednesday[]");
				String[] thursday = req.getParameterValues("thursday[]");
				String[] friday = req.getParameterValues("friday[]");

				// build list
				List<Object> timeEntries = new ArrayList<Object>();
				double total = 0d;

				int rows = 0;
				if (projectIds != null)
					rows = projectIds.length;
				else if (taskIds != null)
					rows = taskIds.length;
				else {
					// fallback : try count of monday fields
					if (monday != null)
						rows = monday.length;
				}

				for (int i = 0; i < rows; i++) {
					try {
						// créer une nouvelle TimeEntry
						TimeEntry te = new TimeEntry();

						// lookup project / task publication objects via Channel (robuste)
						Object projObj = null;
						if (projectIds != null && i < projectIds.length && projectIds[i] != null
								&& projectIds[i].trim().length() > 0) {
							projObj = lookupPublication(projectIds[i].trim(), "Project");
						}
						Object taskObj = null;
						if (taskIds != null && i < taskIds.length && taskIds[i] != null
								&& taskIds[i].trim().length() > 0) {
							taskObj = lookupPublication(taskIds[i].trim(), "ProjectTask");
						}

						// assign project/task (essayer plusieurs setter possibles)
						if (projObj != null) {
							try {
								Method m = te.getClass().getMethod("setProjectRef", projObj.getClass());
								m.invoke(te, projObj);
							} catch (Exception e) {
								try {
									Method m2 = te.getClass().getMethod("setProject", projObj.getClass());
									m2.invoke(te, projObj);
								} catch (Exception e2) {
									// fallback : setProjectRef(String)
									try {
										Method m3 = te.getClass().getMethod("setProjectRef", String.class);
										m3.invoke(te, projectIds[i].trim());
									} catch (Exception ignore) {
									}
								}
							}
						}

						if (taskObj != null) {
							try {
								Method m = te.getClass().getMethod("setTaskRef", taskObj.getClass());
								m.invoke(te, taskObj);
							} catch (Exception e) {
								try {
									Method m2 = te.getClass().getMethod("setTask", taskObj.getClass());
									m2.invoke(te, taskObj);
								} catch (Exception e2) {
									try {
										Method m3 = te.getClass().getMethod("setTaskRef", String.class);
										m3.invoke(te, taskIds[i].trim());
									} catch (Exception ignore) {
									}
								}
							}
						}

						// set days: essayer setMonday(double) ou setMonday(String)
						double rowTotal = 0d;
						rowTotal += setDayDoubleIfPossible(te, "Monday", monday, i);
						rowTotal += setDayDoubleIfPossible(te, "Tuesday", tuesday, i);
						rowTotal += setDayDoubleIfPossible(te, "Wednesday", wednesday, i);
						rowTotal += setDayDoubleIfPossible(te, "Thursday", thursday, i);
						rowTotal += setDayDoubleIfPossible(te, "Friday", friday, i);

						// si TimeEntry possède setTotal or setSum, essayer de renseigner
						try {
							Method mTot = te.getClass().getMethod("setTotal", double.class);
							mTot.invoke(te, rowTotal);
						} catch (Exception e) {
							try {
								Method mTot2 = te.getClass().getMethod("setTotalHours", double.class);
								mTot2.invoke(te, rowTotal);
							} catch (Exception ignore) {
							}
						}

						total += rowTotal;
						timeEntries.add(te);
					} catch (Throwable t) {
						// continuer avec les autres lignes
					}
				} // end rows

				// attacher la liste à la timesheet via reflection
				if (timesheet != null) {
					try {
						// on essaye setTimeEntries(List<TimeEntry>)
						Method setTE = timesheet.getClass().getMethod("setTimeEntries", List.class);
						setTE.invoke(timesheet, timeEntries);
					} catch (Exception e) {
						// fallback : getTimeEntries() returns List -> addAll
						try {
							Method getTE = timesheet.getClass().getMethod("getTimeEntries");
							Object existing = getTE.invoke(timesheet);
							if (existing instanceof List) {
								((List) existing).clear();
								((List) existing).addAll(timeEntries);
							}
						} catch (Exception ignore) {
						}
					}

					// set totalHours on timesheet
					try {
						Method setTot = timesheet.getClass().getMethod("setTotalHours", double.class);
						setTot.invoke(timesheet, total);
					} catch (Exception e) {
						try {
							Method setTot2 = timesheet.getClass().getMethod("setTotalHours", Double.class);
							setTot2.invoke(timesheet, Double.valueOf(total));
						} catch (Exception ignore) {
						}
					}
				}

			} // end if req != null

		} catch (Exception ex) {
			// log si besoin
			try {
				java.util.logging.Logger.getLogger(EditTimesheetHandler.class.getName())
						.warning("Error parsing timesheet entries: " + ex.getMessage());
			} catch (Throwable ignore) {
			}
		}

		// finally, call parent to continue default processing (save) if possible
		try {
			Method m = EditDataHandler.class.getMethod("processAction");
			Object r = m.invoke(this);
			if (r instanceof Boolean)
				return ((Boolean) r).booleanValue();
		} catch (NoSuchMethodException nsme) {
			// si processAction n'existe pas sur super, on essaye saveData()
		} catch (Throwable ignore) {
		}

		// fallback: essayer une méthode saveData() ou deleguer à super
		try {
			Method sd = EditDataHandler.class.getMethod("saveData");
			sd.invoke(this);
		} catch (Throwable ignore) {
			// rien à faire : retourner true pour indiquer traitement (JCMS fera le reste)
		}

		return true;
	}

	// ================= helper reflexive =================

	/**
	 * Tente de convertir la valeur du jour et d'appeler le setter correspondant
	 * (ex: setMonday(double)) Retourne la valeur numérique ajoutée au total (0 si
	 * none)
	 */
	private double setDayDoubleIfPossible(Object te, String dayCamelCase, String[] values, int idx) {
		if (values == null || idx >= values.length)
			return 0d;
		String s = values[idx];
		if (s == null || s.trim().length() == 0)
			return 0d;
		double v = 0d;
		try {
			v = Double.parseDouble(s.replace(',', '.'));
		} catch (Exception e) {
			v = 0d;
		}
		try {
			Method m = te.getClass().getMethod("set" + dayCamelCase, double.class);
			m.invoke(te, v);
			return v;
		} catch (Exception e) {
			try {
				Method m2 = te.getClass().getMethod("set" + dayCamelCase, Double.class);
				m2.invoke(te, Double.valueOf(v));
				return v;
			} catch (Exception e2) {
				try {
					Method m3 = te.getClass().getMethod("set" + dayCamelCase.toLowerCase(), double.class);
					m3.invoke(te, v);
					return v;
				} catch (Exception ignore) {
				}
			}
		}
		return v;
	}

	/**
	 * Lookup publication with plusieurs stratégies (Channel.getData(id, Class),
	 * Channel.getPublication(id), Store.get) On renvoie null si non trouvé.
	 */
	private Object lookupPublication(String id, String simpleTypeName) {
		if (id == null || id.trim().length() == 0)
			return null;
		id = id.trim();
		Channel ch = Channel.getChannel();
		if (ch == null)
			return null;

		// 1) try channel.getData(id, Class)
		try {
			// we don't have compile-time Class<T>, so attempt to find a Class by name
			// "generated."+simpleTypeName
			String className = "generated." + simpleTypeName;
			Class<?> cls = null;
			try {
				cls = Class.forName(className);
			} catch (ClassNotFoundException cnf) {
				// fallback : try plugin package same as types - if different adapt
			}
			if (cls != null) {
				try {
					Method m = ch.getClass().getMethod("getData", String.class, Class.class);
					Object pub = m.invoke(ch, id, cls);
					if (cls.isInstance(pub))
						return pub;
				} catch (Throwable ignore) {
				}
			}
		} catch (Throwable ignore) {
		}

		// 2) try channel.getPublication(id)
		try {
			Method mp = ch.getClass().getMethod("getPublication", String.class);
			Object pub = mp.invoke(ch, id);
			if (pub != null)
				return pub;
		} catch (Throwable ignore) {
		}

		// 3) try Channel.getDataSet? or Store lookup - ignore for now
		return null;
	}

}

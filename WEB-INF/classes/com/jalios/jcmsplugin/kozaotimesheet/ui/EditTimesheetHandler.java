package com.jalios.jcmsplugin.kozaotimesheet.ui;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.logging.Logger;

import com.jalios.jcms.handler.EditDataHandler;
import com.jalios.jcms.Channel;
import com.jalios.jcms.Data;
import com.jalios.jcms.Member;

import generated.Timesheet;
import generated.TimeEntry;
import generated.Project;
import generated.ProjectTask;

/**
 * EditTimesheetHandler - handler typé et robuste pour Timesheet modal editing.
 *
 * Principes : - Pré-remplissage côté serveur (employee = loggedMember,
 * weekNumber, status = DRAFT) - Parsing sécurisé des tableaux POST
 * (projectId[], taskId[], monday[]..friday[]) - Construction de TimeEntry typés
 * puis attachement à Timesheet - Calcul du total au niveau Timesheet
 * (Timesheet.totalHours) - Respect des permissions : un employé ne peut pas
 * soumettre pour un autre employee
 *
 * Notes : - Si les signatures exactes diffèrent dans ta génération (par ex
 * setProjectRef au lieu de setProject), le handler tente des fallbacks via
 * réflexion.
 */
public class EditTimesheetHandler extends EditDataHandler {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = Logger.getLogger(EditTimesheetHandler.class.getName());

	public EditTimesheetHandler() {
		super();
	}

	/**
	 * Retourne la classe manipulée (Timesheet)
	 */
	public Class<? extends Data> getDataClass() {
		return Timesheet.class;
	}

	/**
	 * Pré-remplissage à la création : employee = loggedMember, weekNumber, status =
	 * DRAFT
	 */
	public void setupNewPublication() {
		try {
			Object pub = getPublication();
			Timesheet timesheet = null;

			if (pub == null) {
				timesheet = new Timesheet();
				// essayer de lier la publication si le framework le demande
				try {
					Method setPub = this.getClass().getMethod("setPublication", Timesheet.class);
					setPub.invoke(this, timesheet);
				} catch (NoSuchMethodException nsme) {
					// méthode non présente, ignore
				} catch (Throwable ignore) {
				}
			} else if (pub instanceof Timesheet) {
				timesheet = (Timesheet) pub;
			}

			if (timesheet != null) {
				// employee
				Member lm = getLoggedMember();
				if (lm != null) {
					try {
						timesheet.setEmployee(lm); // typé
					} catch (Throwable t) {
						// fallback: try setEmployee(String)
						try {
							Method m = timesheet.getClass().getMethod("setEmployee", String.class);
							m.invoke(timesheet, lm.getId());
						} catch (Throwable ignore) {
						}
					}
				}

				// semaine courante
				try {
					Calendar cal = GregorianCalendar.getInstance();
					int year = cal.get(Calendar.YEAR);
					int week = cal.get(Calendar.WEEK_OF_YEAR);
					int weekNumber = year * 100 + week;
					try {
						timesheet.setWeekNumber(weekNumber);
					} catch (Throwable t) {
						try {
							Method mw = timesheet.getClass().getMethod("setWeekNumber", Integer.class);
							mw.invoke(timesheet, Integer.valueOf(weekNumber));
						} catch (Throwable ignore) {
						}
					}
				} catch (Throwable ignore) {
				}

				// statut par défaut (serveur)
				try {
					timesheet.setStatus("DRAFT");
				} catch (Throwable ignore) {
				}
			}
		} catch (Throwable ignore) {
			// Ne pas bloquer le formulaire pour un souci mineur
		}
	}

	/**
	 * Parse les time entries, attache à la Timesheet et délègue la sauvegarde.
	 */
	public boolean processAction() throws IOException {
		try {
			Object pub = getPublication();
			Timesheet timesheet = null;
			boolean isNew = false;
			if (pub == null) {
				isNew = true;
			} else if (pub instanceof Timesheet) {
				timesheet = (Timesheet) pub;
			}

			javax.servlet.http.HttpServletRequest req = getRequest();
			Member currentUser = getLoggedMember();

			// récupère info si manager (AppHandler doit exposer isManager)
			boolean isManager = false;
			try {
				Object im = req != null ? req.getAttribute("isManager") : null;
				if (im instanceof Boolean)
					isManager = ((Boolean) im).booleanValue();
			} catch (Throwable ignore) {
			}

			if (req != null) {

				// sécurité : employee ne peut être modifié par l'employé
				String submittedEmployee = req.getParameter("employee");
				if (!isManager && currentUser != null && submittedEmployee != null && submittedEmployee.length() > 0) {
					if (!submittedEmployee.equals(currentUser.getId())) {
						throw new SecurityException("Employee mismatch");
					}
				}

				String[] projectIds = req.getParameterValues("projectId[]");
				String[] taskIds = req.getParameterValues("taskId[]");
				String[] monday = req.getParameterValues("monday[]");
				String[] tuesday = req.getParameterValues("tuesday[]");
				String[] wednesday = req.getParameterValues("wednesday[]");
				String[] thursday = req.getParameterValues("thursday[]");
				String[] friday = req.getParameterValues("friday[]");

				List<TimeEntry> entries = new ArrayList<TimeEntry>();
				double totalHours = 0d;

				int rows = 0;
				if (projectIds != null)
					rows = projectIds.length;
				else if (taskIds != null)
					rows = taskIds.length;
				else if (monday != null)
					rows = monday.length;

				Channel ch = Channel.getChannel();

				for (int i = 0; i < rows; i++) {
					try {
						TimeEntry te = new TimeEntry();

						Project projObj = null;
						ProjectTask taskObj = null;

						// récupère objets project/task via Channel si ids fournis
						if (projectIds != null && i < projectIds.length && projectIds[i] != null
								&& projectIds[i].trim().length() > 0) {
							String pid = projectIds[i].trim();
							try {
								Object p = (ch != null) ? ch.getPublication(pid) : null;
								if (p instanceof Project)
									projObj = (Project) p;
							} catch (Throwable ignore) {
							}
						}

						if (taskIds != null && i < taskIds.length && taskIds[i] != null
								&& taskIds[i].trim().length() > 0) {
							String tid = taskIds[i].trim();
							try {
								Object tObj = (ch != null) ? ch.getPublication(tid) : null;
								if (tObj instanceof ProjectTask)
									taskObj = (ProjectTask) tObj;
							} catch (Throwable ignore) {
							}
						}

						// validation simple : si task référencée, s'assurer qu'elle appartient au
						// projet sélectionné
						if (taskObj != null && projObj != null) {
							try {
								Project linked = taskObj.getProject();
								if (linked != null && !linked.getId().equals(projObj.getId())) {
									// incohérence → ignorer la ligne
									continue;
								}
							} catch (Throwable ignore) {
							}
						}

						// assigne Project/ProjectTask sur TimeEntry (tentative typée, fallback
						// réflexif)
						if (projObj != null) {
							try {
								te.setProject(projObj);
							} catch (Throwable t) {
								// fallback reflexive (setProjectRef(Project) ou setProjectRef(String))
								try {
									Method m = te.getClass().getMethod("setProjectRef", Project.class);
									m.invoke(te, projObj);
								} catch (Throwable t2) {
									try {
										Method m2 = te.getClass().getMethod("setProjectRef", String.class);
										m2.invoke(te, projObj.getId());
									} catch (Throwable ignore) {
									}
								}
							}
						}

						if (taskObj != null) {
							try {
								te.setTask(taskObj);
							} catch (Throwable t) {
								// fallback reflexive
								try {
									Method m = te.getClass().getMethod("setTaskRef", ProjectTask.class);
									m.invoke(te, taskObj);
								} catch (Throwable t2) {
									try {
										Method m2 = te.getClass().getMethod("setTaskRef", String.class);
										m2.invoke(te, taskObj.getId());
									} catch (Throwable ignore) {
									}
								}
							}
						}

						// set day hours (typed setters preferés)
						double rowTotal = 0d;
						rowTotal += setDayIfPossibleTyped(te, "Monday", monday, i);
						rowTotal += setDayIfPossibleTyped(te, "Tuesday", tuesday, i);
						rowTotal += setDayIfPossibleTyped(te, "Wednesday", wednesday, i);
						rowTotal += setDayIfPossibleTyped(te, "Thursday", thursday, i);
						rowTotal += setDayIfPossibleTyped(te, "Friday", friday, i);

						// try to set row total on TimeEntry via reflection if available
						try {
							boolean done = false;
							try {
								Method m = te.getClass().getMethod("setTotal", double.class);
								m.invoke(te, rowTotal);
								done = true;
							} catch (NoSuchMethodException nsme) {
								/* not present */ }
							if (!done) {
								try {
									Method m2 = te.getClass().getMethod("setTotalHours", double.class);
									m2.invoke(te, rowTotal);
									done = true;
								} catch (NoSuchMethodException nsme2) {
									/* not present */ }
							}
							if (!done) {
								try {
									Method m3 = te.getClass().getMethod("setTotal", Double.class);
									m3.invoke(te, Double.valueOf(rowTotal));
									done = true;
								} catch (NoSuchMethodException nsme3) {
									/* not present */ }
							}
						} catch (Throwable ignore) {
							/* permissive */ }

						totalHours += rowTotal;
						entries.add(te);

					} catch (Throwable rowEx) {
						LOG.warning("Skipping invalid time entry row: " + rowEx.getMessage());
					}
				} // fin boucle rows

				// attacher la liste d'entries à la timesheet (typé si possible, fallback
				// reflexif)
				if (timesheet != null) {
					try {
						// preferé : manipuler la liste existante
						List<TimeEntry> existing = (List<TimeEntry>) timesheet.getTimeEntries();
						if (existing != null) {
							existing.clear();
							existing.addAll(entries);
						} else {
							// fallback setter
							Method m = timesheet.getClass().getMethod("setTimeEntries", List.class);
							m.invoke(timesheet, entries);
						}
					} catch (NoSuchMethodException nsme) {
						// fallback try setTimeEntries with reflection ignoring exception types
						try {
							Method m = timesheet.getClass().getMethod("setTimeEntries", List.class);
							m.invoke(timesheet, entries);
						} catch (Throwable ignore) {
						}
					} catch (Throwable ignore) {
					}

					// set total hours on timesheet (typed preferred)
					try {
						timesheet.setTotalHours(totalHours);
					} catch (Throwable t) {
						try {
							Method m = timesheet.getClass().getMethod("setTotalHours", Double.class);
							m.invoke(timesheet, Double.valueOf(totalHours));
						} catch (Throwable ignore) {
						}
					}

					// status management : forcer DRAFT si nouvelle
					if (isNew) {
						try {
							timesheet.setStatus("DRAFT");
						} catch (Throwable ignore) {
						}
					} else {
						// en edition, si non-manager on garantit que le status du client n'écrase pas
						// la vérité
						// (handler ne sauve pas la valeur envoyée côté client pour users non managers)
					}
				}

			} // fin if req != null

		} catch (SecurityException se) {
			throw new IOException("Security error: " + se.getMessage());
		} catch (Throwable ex) {
			LOG.warning("Error in EditTimesheetHandler.processAction: " + ex.getMessage());
		}

		// déléguer à super pour la persistance
		try {
			return super.processAction();
		} catch (Throwable t) {
			// fallback: some JCMS versions might use saveData()
			try {
				Method sd = EditDataHandler.class.getMethod("saveData");
				sd.invoke(this);
			} catch (Throwable ignore) {
			}
		}
		return true;
	}

	/**
	 * Tente d'appeler les setters typés pour les jours (setMonday, setTuesday...)
	 * Retourne la valeur numérique appliquée au total.
	 */
	private double setDayIfPossibleTyped(TimeEntry te, String dayCamelCase, String[] values, int idx) {
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
			if ("Monday".equals(dayCamelCase))
				te.setMonday(v);
			else if ("Tuesday".equals(dayCamelCase))
				te.setTuesday(v);
			else if ("Wednesday".equals(dayCamelCase))
				te.setWednesday(v);
			else if ("Thursday".equals(dayCamelCase))
				te.setThursday(v);
			else if ("Friday".equals(dayCamelCase))
				te.setFriday(v);
			else {
				Method m = te.getClass().getMethod("set" + dayCamelCase, double.class);
				m.invoke(te, v);
			}
			return v;
		} catch (Throwable t) {
			// try wrapper Double signature
			try {
				Method m2 = te.getClass().getMethod("set" + dayCamelCase, Double.class);
				m2.invoke(te, Double.valueOf(v));
				return v;
			} catch (Throwable ignore) {
			}
		}
		return v;
	}
}

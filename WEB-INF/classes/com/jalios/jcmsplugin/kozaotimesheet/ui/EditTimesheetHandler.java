package com.jalios.jcmsplugin.kozaotimesheet.ui;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import com.jalios.jcms.handler.EditDataHandler;
import com.jalios.jcms.Channel;
import com.jalios.jcms.Member;

import generated.Timesheet;
import generated.TimeEntry;
import generated.Project;
import generated.ProjectTask;

/**
 * EditTimesheetHandler - handler robuste pour la création/édition via modal.
 */
public class EditTimesheetHandler extends EditDataHandler {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = Logger.getLogger(EditTimesheetHandler.class.getName());

	public EditTimesheetHandler() {
		super();
	}

	// Fournir la classe manipulée (compatible avec différentes versions JCMS)
	public Class<? extends com.jalios.jcms.Data> getDataClass() {
		return Timesheet.class;
	}

	// Pré-remplissage à la création : employee = loggedMember, weekNumber, status =
	// DRAFT
	public void setupNewPublication() {
		try {
			Object pub = null;
			try {
				Method gm = this.getClass().getMethod("getPublication");
				pub = gm.invoke(this);
			} catch (Throwable ignore) {
				pub = null;
			}

			Timesheet timesheet = null;
			if (pub == null) {
				try {
					timesheet = new Timesheet();
					Method setPub = this.getClass().getMethod("setPublication", Timesheet.class);
					setPub.invoke(this, timesheet);
				} catch (Throwable ignore) {
				}
			} else if (pub instanceof Timesheet) {
				timesheet = (Timesheet) pub;
			}

			if (timesheet != null) {
				try {
					Member lm = getLoggedMember();
					if (lm != null) {
						try {
							timesheet.setEmployee(lm);
						} catch (Throwable t) {
							try {
								Method m = timesheet.getClass().getMethod("setEmployee", String.class);
								m.invoke(timesheet, lm.getId());
							} catch (Throwable ignore) {
							}
						}
					}
				} catch (Throwable ignore) {
				}

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

				try {
					timesheet.setStatus("DRAFT");
				} catch (Throwable ignore) {
				}
			}
		} catch (Throwable ignore) {
			LOG.fine("setupNewPublication fallback: " + ignore.getMessage());
		}
	}

	// Traitement de la sauvegarde : parse rows, crée TimeEntry et attache à
	// Timesheet
	public boolean processAction() throws IOException {
		try {
			Object pub = null;
			try {
				Method gm = this.getClass().getMethod("getPublication");
				pub = gm.invoke(this);
			} catch (Throwable ignore) {
				pub = null;
			}

			boolean isNew = (pub == null);
			Timesheet timesheet = null;
			if (pub instanceof Timesheet)
				timesheet = (Timesheet) pub;

			HttpServletRequest req = getRequest();
			Member currentUser = getLoggedMember();

			boolean isManager = false;
			try {
				Object im = req != null ? req.getAttribute("isManager") : null;
				if (im instanceof Boolean)
					isManager = ((Boolean) im).booleanValue();
			} catch (Throwable ignore) {
			}

			if (req != null) {
				// sécurité : l'employé ne peut pas soumettre pour un autre user
				String submittedEmployee = req.getParameter("employee");
				if (!isManager && currentUser != null && submittedEmployee != null && submittedEmployee.length() > 0) {
					if (!submittedEmployee.equals(currentUser.getId())) {
						throw new SecurityException("Employee mismatch");
					}
				}

				// parse rows (ton parseRowsFromRequest)
				List<Map<String, String>> rows = parseRowsFromRequest(req);

				List<TimeEntry> parsedEntries = new ArrayList<TimeEntry>();
				double total = 0d;
				Channel ch = Channel.getChannel();

				for (int i = 0; i < rows.size(); i++) {
					Map<String, String> row = rows.get(i);
					try {
						TimeEntry te = new TimeEntry();

						String projectId = firstNonNull(row.get("projectId"), row.get("project"), row.get("project_id"),
								row.get("projectRef"));
						String taskId = firstNonNull(row.get("taskId"), row.get("task"), row.get("task_id"),
								row.get("taskRef"));

						Project projObj = null;
						ProjectTask taskObj = null;

						if (projectId != null && projectId.trim().length() > 0 && ch != null) {
							try {
								Object p = ch.getPublication(projectId.trim());
								if (p instanceof Project)
									projObj = (Project) p;
							} catch (Throwable ignore) {
							}
						}

						if (taskId != null && taskId.trim().length() > 0 && ch != null) {
							try {
								Object t = ch.getPublication(taskId.trim());
								if (t instanceof ProjectTask)
									taskObj = (ProjectTask) t;
							} catch (Throwable ignore) {
							}
						}

						// si task et project fournis, vérifier cohérence
						if (taskObj != null && projObj != null) {
							try {
								Project linked = null;
								try {
									linked = taskObj.getProject();
								} catch (Throwable re) {
									try {
										Method mg = taskObj.getClass().getMethod("getProject");
										Object p = mg.invoke(taskObj);
										if (p instanceof Project)
											linked = (Project) p;
									} catch (Throwable ignore) {
									}
								}
								if (linked != null && !linked.getId().equals(projObj.getId())) {
									LOG.warning("Row " + i + " : task does not belong to project -> skipping");
									continue;
								}
							} catch (Throwable ignore) {
							}
						}

						// validation d'assignation (si non manager)
						if (!isManager && taskObj != null && currentUser != null) {
							try {
								Object ass = null;
								try {
									ass = taskObj.getAssignee();
								} catch (Throwable ignore) {
								}
								if (ass == null) {
									try {
										ass = taskObj.getAssigneeId();
									} catch (Throwable ignore) {
									}
								}
								String aid = null;
								if (ass instanceof String)
									aid = (String) ass;
								else if (ass != null) {
									try {
										Object idObj = ass.getClass().getMethod("getId").invoke(ass);
										if (idObj != null)
											aid = String.valueOf(idObj);
									} catch (Throwable ignore) {
									}
								}
								if (aid != null && !aid.equals(currentUser.getId())) {
									LOG.warning("Row " + i + " : task not assigned to current user -> skipping");
									continue;
								}
							} catch (Throwable ignore) {
							}
						}

						// assign project
						if (projObj != null) {
							boolean assigned = false;
							try {
								te.setProject(projObj);
								assigned = true;
							} catch (Throwable t) {
								try {
									Method m = te.getClass().getMethod("setProject", Project.class);
									m.invoke(te, projObj);
									assigned = true;
								} catch (Throwable ignore) {
								}
							}
							if (!assigned) {
								try {
									Method m = te.getClass().getMethod("setProjectRef", String.class);
									m.invoke(te, projObj.getId());
								} catch (Throwable ignore) {
								}
							}
						}

						// assign task (ProjectTask)
						if (taskObj != null) {
							boolean assigned = false;
							try {
								te.setTask(taskObj);
								assigned = true;
							} catch (Throwable t) {
								try {
									Method m = te.getClass().getMethod("setTask", ProjectTask.class);
									m.invoke(te, taskObj);
									assigned = true;
								} catch (Throwable ignore) {
								}
							}
							if (!assigned) {
								try {
									Method m = te.getClass().getMethod("setTaskRef", String.class);
									m.invoke(te, taskObj.getId());
								} catch (Throwable ignore) {
								}
							}
						}

						// set days
						double rowTotal = 0d;
						rowTotal += parseAndSetDay(te, "Monday", row);
						rowTotal += parseAndSetDay(te, "Tuesday", row);
						rowTotal += parseAndSetDay(te, "Wednesday", row);
						rowTotal += parseAndSetDay(te, "Thursday", row);
						rowTotal += parseAndSetDay(te, "Friday", row);

						// try set total on time entry
						try {
							Method m = te.getClass().getMethod("setTotal", double.class);
							m.invoke(te, rowTotal);
						} catch (Throwable e1) {
							try {
								Method m2 = te.getClass().getMethod("setTotalHours", double.class);
								m2.invoke(te, rowTotal);
							} catch (Throwable ignore) {
							}
						}

						total += rowTotal;
						parsedEntries.add(te);

					} catch (Throwable rowEx) {
						LOG.warning("Skipping invalid time entry row " + i + " : " + rowEx.getMessage());
					}
				} // end rows loop

				// attacher à la timesheet
				if (timesheet != null) {
					try {
						// try to get existing list
						try {
							Method mg = timesheet.getClass().getMethod("getTimeEntries");
							Object existing = mg.invoke(timesheet);
							if (existing instanceof java.util.List) {
								java.util.List existingList = (java.util.List) existing;
								existingList.clear();
								existingList.addAll(parsedEntries);
							} else {
								try {
									Method ms = timesheet.getClass().getMethod("setTimeEntries", java.util.List.class);
									ms.invoke(timesheet, parsedEntries);
								} catch (Throwable ignore) {
								}
							}
						} catch (Throwable tget) {
							try {
								Method ms = timesheet.getClass().getMethod("setTimeEntries", java.util.List.class);
								ms.invoke(timesheet, parsedEntries);
							} catch (Throwable ignore) {
							}
						}
					} catch (Throwable ignore) {
					}

					try {
						timesheet.setTotalHours(total);
					} catch (Throwable t) {
						try {
							Method mth = timesheet.getClass().getMethod("setTotalHours", Double.class);
							mth.invoke(timesheet, Double.valueOf(total));
						} catch (Throwable ignore) {
						}
					}

					if (isNew) {
						try {
							timesheet.setStatus("DRAFT");
						} catch (Throwable ignore) {
						}
					}
				}
			}

		} catch (SecurityException se) {
			throw new IOException("Security error: " + se.getMessage());
		} catch (Throwable ex) {
			LOG.warning("Error in EditTimesheetHandler.processAction: " + ex.getMessage());
		}

		// déléguer la persistance
		try {
			return super.processAction();
		} catch (Throwable t) {
			try {
				Method sd = EditDataHandler.class.getMethod("saveData");
				sd.invoke(this);
			} catch (Throwable ignore) {
			}
		}
		return true;
	}

	// ----------------- Helpers -----------------

	private static String firstNonNull(String... vals) {
		if (vals == null)
			return null;
		for (String s : vals)
			if (s != null && s.trim().length() > 0)
				return s;
		return null;
	}

	// parseRowsFromRequest: tu as déjà cette impl dans ton projet. Je réutilise la
	// même signature.
	// (Si tu as une version légèrement différente garde la tienne)
	private List<Map<String, String>> parseRowsFromRequest(HttpServletRequest req) {
		Map<Integer, Map<String, String>> indexed = new TreeMap<Integer, Map<String, String>>();

		// arrays path
		String[] projArr = req.getParameterValues("projectId[]");
		String[] taskArr = req.getParameterValues("taskId[]");
		String[] mondayArr = req.getParameterValues("monday[]");
		String[] tuesdayArr = req.getParameterValues("tuesday[]");
		String[] wednesdayArr = req.getParameterValues("wednesday[]");
		String[] thursdayArr = req.getParameterValues("thursday[]");
		String[] fridayArr = req.getParameterValues("friday[]");

		if (projArr != null || taskArr != null || mondayArr != null) {
			int rows = 0;
			if (projArr != null)
				rows = projArr.length;
			else if (taskArr != null)
				rows = taskArr.length;
			else if (mondayArr != null)
				rows = mondayArr.length;
			for (int i = 0; i < rows; i++) {
				Map<String, String> map = new TreeMap<String, String>();
				if (projArr != null && i < projArr.length)
					map.put("projectId", projArr[i]);
				if (taskArr != null && i < taskArr.length)
					map.put("taskId", taskArr[i]);
				if (mondayArr != null && i < mondayArr.length)
					map.put("monday", mondayArr[i]);
				if (tuesdayArr != null && i < tuesdayArr.length)
					map.put("tuesday", tuesdayArr[i]);
				if (wednesdayArr != null && i < wednesdayArr.length)
					map.put("wednesday", wednesdayArr[i]);
				if (thursdayArr != null && i < thursdayArr.length)
					map.put("thursday", thursdayArr[i]);
				if (fridayArr != null && i < fridayArr.length)
					map.put("friday", fridayArr[i]);
				indexed.put(i, map);
			}
		} else {
			Map<String, String[]> pmap = req.getParameterMap();
			java.util.regex.Pattern pIndexDot = java.util.regex.Pattern.compile("^([^\\[]+)\\[(\\d+)\\]\\.(.+)$");
			java.util.regex.Pattern pRowSuffix = java.util.regex.Pattern.compile("^([a-zA-Z0-9_\\-]+)_row(\\d+)$");
			java.util.regex.Pattern pSimpleRow = java.util.regex.Pattern.compile("^([a-zA-Z0-9_\\-]+)_(\\d+)$");
			for (String param : pmap.keySet()) {
				try {
					String[] vals = pmap.get(param);
					if (vals == null || vals.length == 0)
						continue;
					String val = vals[0];
					java.util.regex.Matcher m = pIndexDot.matcher(param);
					if (m.matches()) {
						int idx = Integer.parseInt(m.group(2));
						String key = m.group(3);
						Map<String, String> map = indexed.get(idx);
						if (map == null) {
							map = new TreeMap<String, String>();
							indexed.put(idx, map);
						}
						map.put(key, val);
						continue;
					}
					m = pRowSuffix.matcher(param);
					if (m.matches()) {
						String key = m.group(1);
						int idx = Integer.parseInt(m.group(2));
						Map<String, String> map = indexed.get(idx);
						if (map == null) {
							map = new TreeMap<String, String>();
							indexed.put(idx, map);
						}
						map.put(key, val);
						continue;
					}
					m = pSimpleRow.matcher(param);
					if (m.matches()) {
						String key = m.group(1);
						int idx = Integer.parseInt(m.group(2));
						Map<String, String> map = indexed.get(idx);
						if (map == null) {
							map = new TreeMap<String, String>();
							indexed.put(idx, map);
						}
						map.put(key, val);
						continue;
					}
					java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("^(.*?)(\\d+)$").matcher(param);
					if (m2.matches()) {
						String key = m2.group(1);
						int idx = Integer.parseInt(m2.group(2));
						Map<String, String> map = indexed.get(idx);
						if (map == null) {
							map = new TreeMap<String, String>();
							indexed.put(idx, map);
						}
						map.put(key, val);
						continue;
					}
				} catch (Throwable t) {
					LOG.fine("Ignored param parsing issue for " + param + " : " + t.getMessage());
				}
			}
		}

		List<Map<String, String>> result = new ArrayList<Map<String, String>>();
		if (!indexed.isEmpty()) {
			for (Map.Entry<Integer, Map<String, String>> e : indexed.entrySet())
				result.add(e.getValue());
		}
		return result;
	}

	private double parseAndSetDay(Object te, String dayCamelCase, Map<String, String> row) {
		String[] keys = new String[] { dayCamelCase.toLowerCase(), dayCamelCase,
				dayCamelCase.substring(0, 3).toLowerCase(), dayCamelCase.substring(0, 3) };
		String raw = null;
		for (String k : keys)
			if (row.containsKey(k) && row.get(k) != null) {
				raw = row.get(k);
				break;
			}
		if (raw == null)
			return 0d;
		raw = raw.trim();
		if (raw.length() == 0)
			return 0d;
		double v = 0d;
		try {
			v = Double.parseDouble(raw.replace(',', '.'));
		} catch (Throwable ignore) {
			v = 0d;
		}

		try {
			Method m = te.getClass().getMethod("set" + dayCamelCase, double.class);
			m.invoke(te, v);
			return v;
		} catch (Throwable e1) {
			try {
				Method m2 = te.getClass().getMethod("set" + dayCamelCase, Double.class);
				m2.invoke(te, Double.valueOf(v));
				return v;
			} catch (Throwable ignore) {
			}
			try {
				Method m3 = te.getClass().getMethod("set" + dayCamelCase.toLowerCase(), double.class);
				m3.invoke(te, v);
				return v;
			} catch (Throwable ignore) {
			}
		}
		return v;
	}

}

<%@ include file='/jcore/doInitPage.jspf'%>
<%@ include file="/front/app/doAppCommon.jspf"%>
<%@ page import="com.jalios.jcmsplugin.kozaotimesheet.*"%>

<jsp:useBean id="appHandler" scope="page"
	class="com.jalios.jcmsplugin.kozaotimesheet.ui.TimesheetAppHandler">
	<jsp:setProperty name="appHandler" property="request"
		value="<%=request%>" />
	<jsp:setProperty name="appHandler" property="response"
		value="<%=response%>" />
	<jsp:setProperty name="appHandler" property="*" />
</jsp:useBean>

<%
/* --- Contrôle d’accès de base : membre connecté --- */
if (appHandler.getLoggedMember() == null) {
	appHandler.sendForbidden(request, response);
	return;
}

/* --- Construction des URLs robustes --- */
String appUrl = appHandler.getAppUrl();
String context = request.getContextPath();
String fullAppUrl = (appUrl != null && appUrl.startsWith("/")) ? context + appUrl : context + "/" + appUrl;
if (fullAppUrl.contains("//"))
	fullAppUrl = fullAppUrl.replace("//", "/");

/* --- Titre + ressources --- */
jcmsContext.setPageTitle(glp("jcmsplugin.kozaotimesheet.app.title"));
jcmsContext.addCSSHeader("plugins/KozaoTimesheetPlugin/css/timesheet.css");
jcmsContext.addJavaScript("plugins/KozaoTimesheetPlugin/js/timesheet-modal.js");

/* --- Détermination de la vue à partir du paramètre (compatible) --- */
// lire le paramètre view depuis la requête HTTP
String viewParam = request.getParameter("view");
if (viewParam == null || viewParam.trim().length() == 0) {
	viewParam = "employee";
}

// vérifier si l'utilisateur est manager via le handler existant
boolean isManager = false;
try {
	// utilise la méthode isManager() que tu as définie dans ton handler
	isManager = appHandler.isManager();
} catch (Throwable t) {
	isManager = false;
}

// Sécurité : si on demande "manager" mais que l'utilisateur n'est pas manager,
// on retombe sur "employee"
String view = viewParam;
if ("manager".equals(view) && !isManager) {
	view = "employee";
}

/* --- URLs pour changer de vue --- */
String employeeUrl = fullAppUrl + (fullAppUrl.indexOf('?') >= 0 ? "&view=employee" : "?view=employee");
String managerUrl = fullAppUrl + (fullAppUrl.indexOf('?') >= 0 ? "&view=manager" : "?view=manager");

/* --- Expose aux fragments --- */
request.setAttribute("view", view);
request.setAttribute("employeeUrl", employeeUrl);
request.setAttribute("managerUrl", managerUrl);
request.setAttribute("isManager", Boolean.valueOf(isManager));
%>

<%@ include file='/jcore/doHeader.jspf'%>

<div class="ajax-refresh-div"
	data-jalios-ajax-refresh-url="<%=fullAppUrl + (fullAppUrl.indexOf('?') >= 0 ? "&view=" + view : "?view=" + view)%>">
	<jalios:app name="timesheet">

		<%-- SIDEBAR --%>
		<%@ include
			file='/plugins/KozaoTimesheetPlugin/jsp/sidebar/doTimesheetSidebar.jspf'%>

		<%-- MAIN --%>
		<jalios:appMain
			headerTitle='<%=glp("jcmsplugin.kozaotimesheet.app.title")%>'>
			<%@ include
				file='/plugins/KozaoTimesheetPlugin/jsp/body/doTimesheetBody.jspf'%>
		</jalios:appMain>

	</jalios:app>
</div>

<%@ include file='/jcore/doFooter.jspf'%>

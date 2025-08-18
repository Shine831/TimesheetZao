<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.jalios.jcms.uicomponent.app.AppConstants"%>
<%@ include file="/jcore/doInitPage.jspf"%>

<%
String includePath = "/types/Timesheet/editTimesheetModal.jsp";
request.setAttribute(AppConstants.BODY_INCLUDE_PATH_REQUEST_ATTR, includePath);
request.setAttribute(AppConstants.BODY_INCLUDE_PUB_REQUEST_ATTR, null);
%>

<jalios:include jsp="/front/app/doAppBodyInclude.jsp" />

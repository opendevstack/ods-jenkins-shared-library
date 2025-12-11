package org.ods.services.documents.snow.client

// Entry point
def config = new ServiceNowConfig()
def client = new ServiceNowClient(config)

def roleKeys = ["System Lead", "Validation Manager Delegate(s)", "Validation Manager", "System Owner Delegate(s)", "System Lead Delegate(s)", "System Owner", "CSV&C/QA Delegate(s)", "CSV&C/QA Delegate(s)", "System Owner", "System Owner Delegate(s)", "CSV&C/QA Contact"] as Set

def token = client.getAccessToken()
List<Map> businessApplicationRoles = client.getBusinessApplicationRoles(token)

def emails  = client.getUserEmails(token, businessApplicationRoles, roleKeys)

println("Emails: " + emails)


package DIRBulkUpdate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.time.Instant;

import org.json.JSONObject;

public class SAPOperations implements Runnable {
	String i_chronicle_id;
	String operation;
	JSONObject boIdsJson;
	BUProperties bu;
	Integer sno;
	CountDownLatch sapLatch;
	String dirNumber;
	String plant; // R4A
	String title; // R4A

	// R4A
	public SAPOperations(JSONObject boIdsJson, String i_chronicle_id, String operation, BUProperties bu, Integer sno,
			CountDownLatch sapLatch, String dirNumber, String planningPlant, String title) {
		this.i_chronicle_id = i_chronicle_id;
		this.bu = bu;
		this.operation = operation;
		this.boIdsJson = boIdsJson;
		this.sno = sno;
		this.sapLatch = sapLatch;
		this.dirNumber = dirNumber;
		this.plant = planningPlant; // R4A
		this.title = title; // R4A
	}

	@Override
	public void run() {
		try {
			HttpClient client = HttpClient.newHttpClient();
			String token;
			token = checkAuthToken(bu.authToken);
			if (token.equals("")) {
				DIRBulkUpdate.logger.info("{}:{} [BAPI] Fetching new auth token", bu.buName,
						Thread.currentThread().getName());
				token = DatabaseFunctions.getAuthToken(bu);
			}

			if (token != null && !token.trim().isEmpty()) {
				if (operation.equals("CREATE")) {
					if (boIdsJson.toString().equals("{}")) {
						DatabaseFunctions.updateDbPostBapi(bu, sno, "No object links, DIR not necessary.",
								Constants.completedStatus, null);
					} else {
						callCreateBapi(i_chronicle_id, boIdsJson, bu, client, token, sno, plant, title); // R4A
					}
				} else if (operation.equals("UPDATE")) {
					if (boIdsJson.toString().equals("{}")) {
						DatabaseFunctions.updateDbPostBapi(bu, sno, "No object links, DIR update not necessary.",
								Constants.completedStatus, dirNumber);
					} else {
						callUpdateBapi(i_chronicle_id, boIdsJson, dirNumber, client, token, sno, plant); // R4A
					}
				} else if (operation.equals("DELETE")) {
					this.boIdsJson = DatabaseFunctions.FetchBoIds(bu, i_chronicle_id);
					callUpdateBapi(i_chronicle_id, boIdsJson, dirNumber, client, token, sno, plant); // R2C
					callDeleteBapi(dirNumber, client, token, sno, plant);
				}
			}

			sapLatch.countDown();
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [BAPI] Unexpected error in SAP operation sno={}", bu.buName,
					Thread.currentThread().getName(), sno, e);
			DatabaseFunctions.updateDbPostBapi(bu, sno, "Failed to call BAPI", Constants.errorStatus, null);
		}
	}

	private void callUpdateBapi(String i_chronicle_id, JSONObject boIdsJson, String dirNumber, HttpClient client,
			String token, Integer sno, String plant) {
		DIRBulkUpdate.logger.info("{}:{} [BAPI] UPDATE objectId={} plant={}", bu.buName,
				Thread.currentThread().getName(), i_chronicle_id, plant);

		try {
			String payLoad = PayloadBuilder.buildUpdatePayload(boIdsJson, i_chronicle_id, bu, dirNumber);
			DIRBulkUpdate.logger.debug("{}:{} [BAPI] UPDATE payload: {}", bu.buName, Thread.currentThread().getName(),
					payLoad);

			HttpRequest request = (HttpRequest) HttpRequest.newBuilder().uri(URI.create(bu.endpoint))
					.header("Content-Type", "application/json").header("APIKey", bu.apiKey)
					.header("Authorization", "Bearer " + token).header("CompanyCode", bu.companyCode)
					.header("MaintenancePlanningPlant", plant).header("Accept", "application/json")
					.method("PATCH", HttpRequest.BodyPublishers.ofString(payLoad)).build();

			DIRBulkUpdate.logger.debug("{}:{} [BAPI] UPDATE request → PATCH {} plant={} companyCode={}", bu.buName,
					Thread.currentThread().getName(), bu.endpoint, plant, bu.companyCode);
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			DIRBulkUpdate.logger.debug("{}:{} [BAPI] UPDATE response: {}", bu.buName, Thread.currentThread().getName(),
					response.body());

			JSONObject res = new JSONObject(response.body());

			if (res.getJSONObject(Constants.updateResponse).getJSONObject(Constants.ret).get(Constants.message)
					.toString().equals("")) {
				String versionId = bu.docsWithNewVersion.get(i_chronicle_id).keySet().iterator().next();
				if (bu.docsWithDir.get(i_chronicle_id)
						.equals(bu.docsWithNewVersion.get(i_chronicle_id).get(versionId))) {
					DatabaseFunctions.updateDbPostBapi(bu, sno,
							bu.messages.get(i_chronicle_id) + "DIR Updated Successfully.", Constants.completedStatus,
							dirNumber);
				} else {
					DatabaseFunctions.updateDbPostBapi(bu, sno,
							"DIR Successfully Updated, but Document DIR number not updated.",
							Constants.partCompletedStatus, dirNumber);
					DocumentumFunctions.addDirNumber(dirNumber, versionId, bu, sno,
							bu.messages.get(i_chronicle_id) + "DIR successfully updated and Document updated.");
				}
			} else {
				DatabaseFunctions.updateDbPostBapi(bu, sno,
						bu.messages.get(i_chronicle_id) + "Error: " + res.getJSONObject(Constants.updateResponse)
								.getJSONObject(Constants.ret).getString(Constants.message),
						Constants.errorStatus, dirNumber);
			}
		} catch (Exception e) {
			DatabaseFunctions.updateDbPostBapi(bu, sno,
					bu.messages.get(i_chronicle_id) + "DIR Update Failed. Error: " + e.getMessage(),
					Constants.errorStatus, dirNumber);
			DIRBulkUpdate.logger.error("{}:{} [BAPI] UPDATE failed for objectId={}", bu.buName,
					Thread.currentThread().getName(), i_chronicle_id, e);
		}
	}

	private void callCreateBapi(String i_chronicle_id, JSONObject boIdsJson, BUProperties bu, HttpClient client,
			String token, Integer sno, String plant, String title) { // R4A
		DIRBulkUpdate.logger.info("{}:{} [BAPI] CREATE objectId={} plant={}", bu.buName,
				Thread.currentThread().getName(), i_chronicle_id, plant);

		try {
			String payLoad = PayloadBuilder.buildPayload(boIdsJson, i_chronicle_id, bu, title); // R4A
			DIRBulkUpdate.logger.debug("{}:{} [BAPI] CREATE payload: {}", bu.buName, Thread.currentThread().getName(),
					payLoad);

			HttpRequest request = (HttpRequest) HttpRequest.newBuilder().uri(URI.create(bu.endpoint))
					.header("Content-Type", "application/json").header("APIKey", bu.apiKey)
					.header("Authorization", "Bearer " + token).header("CompanyCode", bu.companyCode)
					.header("MaintenancePlanningPlant", plant).header("Accept", "application/json")
					.method("POST", HttpRequest.BodyPublishers.ofString(payLoad)).build();

			DIRBulkUpdate.logger.debug("{}:{} [BAPI] CREATE request → POST {} plant={} companyCode={}", bu.buName,
					Thread.currentThread().getName(), bu.endpoint, plant, bu.companyCode);
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			DIRBulkUpdate.logger.debug("{}:{} [BAPI] CREATE response: {}", bu.buName, Thread.currentThread().getName(),
					response.body());

			JSONObject res = new JSONObject(response.body());

			// R4A
			if (res.has("error")) {
				JSONObject error = res.getJSONObject("error");
				String code = error.optString("code", "");
				String message = error.getJSONObject("message").optString("value", "");
				String innerMsg = "";

				if (error.has("innererror")) {
					JSONObject inner = error.getJSONObject("innererror");
					if (inner.has("errordetails")) {
						innerMsg = inner.getJSONArray("errordetails").optJSONObject(0).optString("message", "");
					}
				}

				DatabaseFunctions.updateDbPostBapi(bu, sno,
						bu.messages.get(i_chronicle_id) + "Error: " + message + " | " + innerMsg, Constants.errorStatus,
						null);
			} else {
				if (res.getJSONObject(Constants.createResponse).getJSONObject(Constants.ret).get(Constants.message)
						.toString().equals("")) {
					String dirNumber = res.getJSONObject(Constants.createResponse)
							.getString(Constants.dirNumberResponse);
					DatabaseFunctions.updateDbPostBapi(bu, sno, "DIR Successfully Created",
							Constants.partCompletedStatus, dirNumber);
					String versionId = bu.docsWithNewVersion.get(i_chronicle_id).keySet().iterator().next();
					DocumentumFunctions.addDirNumber(dirNumber, versionId, bu, sno,
							bu.messages.get(i_chronicle_id) + "DIR successfully created and Document updated.");
				} else {
					DatabaseFunctions.updateDbPostBapi(bu, sno,
							bu.messages.get(i_chronicle_id) + "Error: " + res.getJSONObject(Constants.createResponse)
									.getJSONObject(Constants.ret).get(Constants.message),
							Constants.errorStatus, null);
				}
			}
		} catch (Exception e) {
			DatabaseFunctions.updateDbPostBapi(bu, sno,
					bu.messages.get(i_chronicle_id) + "DIR Creation Failed. Error: " + e.getMessage(),
					Constants.errorStatus, null);
			DIRBulkUpdate.logger.error("{}:{} [BAPI] CREATE failed for objectId={}", bu.buName,
					Thread.currentThread().getName(), i_chronicle_id, e);
		}
	}

	private void callDeleteBapi(String dirNumber, HttpClient client, String token, Integer sno, String plant) {
		DIRBulkUpdate.logger.info("{}:{} [BAPI] DELETE objectId={} plant={}", bu.buName,
				Thread.currentThread().getName(), i_chronicle_id, plant);

		try {
			HttpRequest request = (HttpRequest) HttpRequest.newBuilder().uri(URI.create(bu.endpoint))
					.header("Content-Type", "application/json").header("APIKey", bu.apiKey)
					.header("Authorization", "Bearer " + token).header("CompanyCode", bu.companyCode)
					.header("MaintenancePlanningPlant", plant).header("Accept", "application/json")
					.header(Constants.sapDocTypeField, bu.docType).header(Constants.sapDocNumberField, dirNumber)
					.header(Constants.sapDocPartField, bu.docPart).header(Constants.sapDocVersionField, bu.docVersion)
					.method("DELETE", HttpRequest.BodyPublishers.ofString("")).build();

			DIRBulkUpdate.logger.debug("{}:{} [BAPI] DELETE request → DELETE {} plant={} companyCode={} dirNumber={}",
					bu.buName, Thread.currentThread().getName(), bu.endpoint, plant, bu.companyCode, dirNumber);
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			DIRBulkUpdate.logger.debug("{}:{} [BAPI] DELETE response: {}", bu.buName, Thread.currentThread().getName(),
					response.body());

			JSONObject res = new JSONObject(response.body());
			if (res.getJSONObject(Constants.deleteResponse).getJSONObject(Constants.ret).getString(Constants.message)
					.equals("")) {
				DatabaseFunctions.updateDbPostBapi(bu, sno, "Delete Flag Set Successfully.", Constants.completedStatus,
						dirNumber);
			} else {
				DatabaseFunctions.updateDbPostBapi(bu, sno, res.getJSONObject(Constants.deleteResponse)
						.getJSONObject(Constants.ret).getString(Constants.message), Constants.errorStatus, dirNumber);
			}
		} catch (Exception e) {
			DatabaseFunctions.updateDbPostBapi(bu, sno, "DIR Deletion Failed. Error: " + e.getMessage(),
					Constants.errorStatus, dirNumber);
			DIRBulkUpdate.logger.error("{}:{} [BAPI] DELETE failed for dirNumber={}", bu.buName,
					Thread.currentThread().getName(), dirNumber, e);
		}
	}


	private String checkAuthToken(JSONObject authToken) {
		try {
			if (authToken == null || !authToken.has("access_token") || !authToken.has("expires_on")) {
				return "";
			}

			long currentEpoch = Instant.now().getEpochSecond();
			long expiryEpoch = Long.parseLong(authToken.getString("expires_on"));
			long remainingSeconds = expiryEpoch - currentEpoch;

			DIRBulkUpdate.logger.debug("{}:{} [BAPI] Token remaining validity: {}s", bu.buName,
					Thread.currentThread().getName(), remainingSeconds);

			if (remainingSeconds > 300) {
				return authToken.getString("access_token");
			}
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [BAPI] Error checking auth token validity", bu.buName,
					Thread.currentThread().getName(), e);
		}

		return "";
	}
}

package DIRBulkUpdate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.time.Instant;

import org.json.JSONObject;

public class SAPOperations implements Runnable {
	String r_object_id;
	String operation;
	JSONObject boIdsJson;
	BUProperties bu;
	Integer sno;
	CountDownLatch sapLatch;
	String dirNumber;
	String plant; // R4A
	String title;

	// R4A
	public SAPOperations(JSONObject boIdsJson, String r_object_id, String operation, BUProperties bu, Integer sno,
			CountDownLatch sapLatch, String dirNumber, String planningPlant, String title) {
		this.r_object_id = r_object_id;
		this.bu = bu;
		this.operation = operation;
		this.boIdsJson = boIdsJson;
		this.sno = sno;
		this.sapLatch = sapLatch;
		this.dirNumber = dirNumber;
		this.plant = planningPlant; // R4A
		this.title = title;
	}

	@Override
	public void run() {
		try {
			HttpClient client = HttpClient.newHttpClient();
			String token;
			token = checkAuthToken(bu.authToken);
			if (token.equals("")) {
				System.out.println("Fetching new token");
				token = DatabaseFunctions.getAuthToken(bu);
			}

			if (token != null && !token.trim().isEmpty()) {
				if (operation.equals("CREATE")) {
					if (boIdsJson.toString().equals("{}")) {
						DatabaseFunctions.updateDbPostBapi(bu, sno, "No object links, DIR not necessary.",
								Constants.completedStatus, null);
					} else {
						callCreateBapi(r_object_id, boIdsJson, bu, client, token, sno, plant, title); // R4A
					}
				} else if (operation.equals("UPDATE")) {
					if (boIdsJson.toString().equals("{}")) {
						DatabaseFunctions.updateDbPostBapi(bu, sno, "No object links, DIR update not necessary.",
								Constants.completedStatus, dirNumber);
					} else {
						callUpdateBapi(r_object_id, boIdsJson, dirNumber, client, token, sno, plant); // R4A
					}
				} else if (operation.equals("DELETE")) {

					callDeleteBapi(dirNumber, client, token, sno, plant);

				}
			}

			sapLatch.countDown();
		} catch (Exception e) {
			e.printStackTrace();
			DatabaseFunctions.updateDbPostBapi(bu, sno, "Failed to call BAPI", Constants.errorStatus, null);
			// DatabaseFunctions.updateDbError(bu, r_object_id, null, "readytoprocess",
			// boIdsJson.toString(), operation,dirNumber);
		}

	}

	private void callUpdateBapi(String r_object_id, JSONObject boIdsJson, String dirNumber, HttpClient client,
			String token, Integer sno, String plant) {
		System.out.println(bu.buName + ":" + Thread.currentThread().getName() + "Operation:UPDATE :Object ID: "
				+ r_object_id + " :Planning Plant: " + plant);

		try {
			String payLoad = PayloadBuilder.buildUpdatePayload(boIdsJson, r_object_id, bu, dirNumber);
//			System.out.println("Update Payload: "+payLoad);
			HttpRequest request = (HttpRequest) HttpRequest.newBuilder().uri(URI.create(bu.endpoint))
					.header("Content-Type", "application/json").header("APIKey", bu.apiKey)
					.header("Authorization", "Bearer " + token).header("CompanyCode", bu.companyCode)
					.header("MaintenancePlanningPlant", plant).header("Accept", "application/json")
					.method("PATCH", HttpRequest.BodyPublishers.ofString(payLoad)).build();

			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + "Operation:UPDATE Request: "
					+ request + "\n" + request.headers());
			DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + "Operation:UPDATE Request: "
					+ request + "\n" + request.headers());
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + "Operation:UPDATE Response: "
					+ response.body() + "\n");
			DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + "Operation:UPDATE Response: "
					+ response.body() + "\n");

			JSONObject res = new JSONObject(response.body());

			if (res.getJSONObject(Constants.updateResponse).getJSONObject(Constants.ret).get(Constants.message)
					.toString().equals("")) {
				String versionId = bu.docsWithNewVersion.get(r_object_id).keySet().iterator().next();
				if (bu.docsWithDir.get(r_object_id).equals(bu.docsWithNewVersion.get(r_object_id).get(versionId))) {
					DatabaseFunctions.updateDbPostBapi(bu, sno,
							bu.messages.get(r_object_id) + "DIR Updated Successfully.", Constants.completedStatus,
							dirNumber);
				} else {
					DatabaseFunctions.updateDbPostBapi(bu, sno,
							"DIR Successfully Updated, but Document DIR number not updated.",
							Constants.partCompletedStatus, dirNumber);
					DocumentumFunctions.addDirNumber(dirNumber, versionId, bu, sno,
							bu.messages.get(r_object_id) + "DIR successfully updated and Document updated.");
				}

			} else {
				DatabaseFunctions.updateDbPostBapi(bu, sno,
						bu.messages.get(r_object_id) + "Error: " + res.getJSONObject(Constants.updateResponse)
								.getJSONObject(Constants.ret).getString(Constants.message),
						Constants.errorStatus, dirNumber);
				/*
				 * DatabaseFunctions.updateDbError(bu, r_object_id, null, "readytoprocess",
				 * boIdsJson.toString(), "UPDATE",dirNumber);
				 */
			}
		} catch (Exception e) {
			DatabaseFunctions.updateDbPostBapi(bu, sno,
					bu.messages.get(r_object_id) + "DIR Update Failed. Error: " + e.getMessage(), Constants.errorStatus,
					dirNumber);
			/*
			 * DatabaseFunctions.updateDbError(bu, r_object_id, null, "readytoprocess",
			 * boIdsJson.toString(), "UPDATE",dirNumber);
			 */
			e.printStackTrace();
		}

	}

	private void callCreateBapi(String r_object_id, JSONObject boIdsJson, BUProperties bu, HttpClient client,
			String token, Integer sno, String plant, String title) { // R4A

		System.out.println(bu.buName + ":" + Thread.currentThread().getName() + "Operation:CREATE :Object ID: "
				+ r_object_id + " :Planning Plant: " + plant);
		try {
			String payLoad = PayloadBuilder.buildPayload(boIdsJson, r_object_id, bu, title); // R4A
//			System.out.println("Create Payload is: "+payLoad);
			// System.out.println("Token : "+token);
			HttpRequest request = (HttpRequest) HttpRequest.newBuilder().uri(URI.create(bu.endpoint))
					.header("Content-Type", "application/json").header("APIKey", bu.apiKey)
					.header("Authorization", "Bearer " + token).header("CompanyCode", bu.companyCode)
					.header("MaintenancePlanningPlant", plant).header("Accept", "application/json")
					.method("POST", HttpRequest.BodyPublishers.ofString(payLoad)).build();

			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + "Operation:CREATE Request: "
					+ request + "\n" + request.headers());
			DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + "Operation:CREATE Request: "
					+ request + "\n" + request.headers());
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + "Operation:CREATE Response: "
					+ response.body() + "\n");
			DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + "Operation:CREATE Response: "
					+ response.body() + "\n");
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

				// Store in DB
				DatabaseFunctions.updateDbPostBapi(bu, sno,
						bu.messages.get(r_object_id) + "Error: " + message + " | " + innerMsg, Constants.errorStatus,
						null);

			} else {
				if (res.getJSONObject(Constants.createResponse).getJSONObject(Constants.ret).get(Constants.message)
						.toString().equals("")) {

					String dirNumber = res.getJSONObject(Constants.createResponse)
							.getString(Constants.dirNumberResponse);
					DatabaseFunctions.updateDbPostBapi(bu, sno, "DIR Successfully Created",
							Constants.partCompletedStatus, dirNumber);
					String versionId = bu.docsWithNewVersion.get(r_object_id).keySet().iterator().next();
					DocumentumFunctions.addDirNumber(dirNumber, versionId, bu, sno,
							bu.messages.get(r_object_id) + "DIR successfully created and Document updated.");
				} else {
					DatabaseFunctions.updateDbPostBapi(bu, sno,
							bu.messages.get(r_object_id) + "Error: " + res.getJSONObject(Constants.createResponse)
									.getJSONObject(Constants.ret).get(Constants.message),
							Constants.errorStatus, null);
					/*
					 * DatabaseFunctions.updateDbError(bu, r_object_id, null, "readytoprocess",
					 * boIdsJson.toString(), "CREATE",null); DatabaseFunctions.updateDbError(bu,
					 * r_object_id, null, "readytoprocess", null, "DELETE",dirNumber);
					 */
				}
			}

		} catch (Exception e) {
			DatabaseFunctions.updateDbPostBapi(bu, sno,
					bu.messages.get(r_object_id) + "DIR Creation Failed. Error: " + e.getMessage(),
					Constants.errorStatus, null);
			/*
			 * DatabaseFunctions.updateDbError(bu, r_object_id, null, "readytoprocess",
			 * boIdsJson.toString(), "CREATE",null); DatabaseFunctions.updateDbError(bu,
			 * r_object_id, null, "readytoprocess", null, "DELETE",dirNumber);
			 */
			e.printStackTrace();
		}

	}

	private void callDeleteBapi(String dirNumber, HttpClient client, String token, Integer sno, String plant) {

		System.out.println(bu.buName + ":" + Thread.currentThread().getName() + "Operation:DELETE :Object ID: "
				+ r_object_id + " :Planning Plant: " + plant);

		try {
			HttpRequest request = (HttpRequest) HttpRequest.newBuilder().uri(URI.create(bu.endpoint))
					.header("Content-Type", "application/json").header("APIKey", bu.apiKey)
					.header("Authorization", "Bearer " + token).header("CompanyCode", bu.companyCode)
					.header("MaintenancePlanningPlant", plant).header("Accept", "application/json")
					.header(Constants.sapDocTypeField, bu.docType).header(Constants.sapDocNumberField, dirNumber)
					.header(Constants.sapDocPartField, bu.docPart).header(Constants.sapDocVersionField, bu.docVersion)
					.method("DELETE", HttpRequest.BodyPublishers.ofString("")).build();

			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + "Operation:DELETE Request: "
					+ request + "\n" + request.headers());
			DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + "Operation:DELETE Request: "
					+ request + "\n" + request.headers());
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + "Operation:DELETE Response: "
					+ response.body() + "\n");
			DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + "Operation:DELETE Response: "
					+ response.body() + "\n");
			JSONObject res = new JSONObject(response.body());
			if (res.getJSONObject(Constants.deleteResponse).getJSONObject(Constants.ret).getString(Constants.message)
					.equals("")) {
				DatabaseFunctions.updateDbPostBapi(bu, sno, "Delete Flag Set Successfully.", Constants.completedStatus,
						dirNumber);
			} else {
				DatabaseFunctions.updateDbPostBapi(bu, sno, res.getJSONObject(Constants.deleteResponse)
						.getJSONObject(Constants.ret).getString(Constants.message), Constants.errorStatus, dirNumber);
				/*
				 * DatabaseFunctions.updateDbError(bu, r_object_id, null, "readytoprocess",
				 * null, "DELETE",dirNumber);
				 */
			}

		} catch (Exception e) {
			DatabaseFunctions.updateDbPostBapi(bu, sno, "DIR Deletion Failed. Error: " + e.getMessage(),
					Constants.errorStatus, dirNumber);
			/*
			 * DatabaseFunctions.updateDbError(bu, r_object_id, null, "readytoprocess",
			 * null, "DELETE",dirNumber);
			 */
			e.printStackTrace();
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

			System.out.println("Token remaining validity: " + remainingSeconds + " seconds");

			if (remainingSeconds > 300) {
				return authToken.getString("access_token");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return "";
	}
}
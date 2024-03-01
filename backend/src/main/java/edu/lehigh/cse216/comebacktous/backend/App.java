package edu.lehigh.cse216.comebacktous.backend;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.TokenRequest;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonString;
import com.google.api.client.json.gson.GsonFactory;
// Import Google's JSON library
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import edu.lehigh.cse216.comebacktous.backend.Database.IdeaRow;
import edu.lehigh.cse216.comebacktous.backend.Database.UserRow;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
//how do i check its function
//deploy?
// Import the Spark package, so that we can make use of the "get" function to
// create an HTTP GET route
import spark.Spark;

/**
 * For now, our app creates an HTTP server that can only get and add data.
 */
public class App {

  public static void main(String[] args) {
    // gson provides us with a way to turn JSON into objects, and objects
    // into JSON.
    //
    // NB: it must be final, so that it can be accessed from our lambdas
    //
    // NB: Gson is thread-safe. See
    // https://stackoverflow.com/questions/10380835/is-it-ok-to-use-gson-instance-as-a-static-field-in-a-model-bean-reuse
    final Gson gson = new Gson();
    Spark.port(Integer.parseInt(System.getenv("PORT")));
    Map<String, String> env = System.getenv();
    // Local Table for user auth
    HashMap<String, String> userTable = new HashMap<String, String>();
    // dataStore holds all of the data that has been provided via HTTP
    // requests
    //
    // NB: every time we shut down the server, we will lose all data, and
    // every time we start the server, we'll have an empty dataStore,
    // with IDs starting over from 0.
    String db_url =
      "postgres://nrhaggnqicsbno:1ffebc514f79d9ba02032a2e174a8f37972c793cb2f9dbc856eba4f9a88d5630@ec2-3-220-207-90.compute-1.amazonaws.com:5432/dam4fg1iidcoa9";
    final Database dataBase = Database.getDatabase(db_url);

    // Set up the location for serving static files. If the STATIC_LOCATION
    // environment variable is set, we will serve from it. Otherwise, serve
    // from "/web"
    String static_location_override = System.getenv("STATIC_LOCATION");
    if (static_location_override == null) {
      Spark.staticFileLocation("/web");
    } else {
      Spark.staticFiles.externalLocation(static_location_override);
    }

    String cors_enabled = env.get("CORS_ENABLED");

    if ("True".equalsIgnoreCase(cors_enabled)) {
      final String acceptCrossOriginRequestsFrom = "*";
      final String acceptedCrossOriginRoutes = "GET,PUT,POST,DELETE,OPTIONS";
      final String supportedRequestHeaders =
        "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin";
      enableCORS(
        acceptCrossOriginRequestsFrom,
        acceptedCrossOriginRoutes,
        supportedRequestHeaders
      );
    }
    // GET route that returns all message titles and Ids. All we do is get
    // the data, embed it in a StructuredResponse, turn it into JSON, and
    // return it. If there's no data, we return "[]", so there's no need
    // for error handling.
    Spark.get(
      "/ideas",
      (request, response) -> { // change message to idea
        // ensure status 200 OK, with a MIME type of JSON
        response.status(200);
        response.type("application/json");
        return gson.toJson(
          new StructuredResponse("ok", null, dataBase.selectAllIdeas())
        );
      }
    );

    // GET route that returns everything for a single row in the DataStore.
    // The ":id" suffix in the first parameter to get() becomes
    // request.params("id"), so that we can get the requested row ID. If
    // ":id" isn't a number, Spark will reply with a status 500 Internal
    // Server Error. Otherwise, we have an integer, and the only possible
    // error is that it doesn't correspond to a row with data.
    Spark.get(
      "/ideas/idea/:id",
      (request, response) -> { // idea in the middle?
        int idx = Integer.parseInt(request.params("id"));
        // ensure status 200 OK, with a MIME type of JSON
        response.status(200);
        response.type("application/json");
        IdeaRow data = dataBase.selectOneIdea(idx);
        if (data == null) {
          return gson.toJson(
            new StructuredResponse("error", idx + " not found", null)
          );
        } else {
          return gson.toJson(new StructuredResponse("ok", null, data));
        }
      }
    );

    // POST route for adding a new element to the DataStore. This will read
    // JSON from the body of the request, turn it into a SimpleRequest
    // object, extract the title and message, insert them, and return the
    // ID of the newly created row.
    // takes a title and idea and uid and adds a new idea to the idea table
    Spark.post(
      "/postidea",
      (request, response) -> {
        System.out.println(request.body());
        // NB: if gson.Json fails, Spark will reply with status 500 Internal
        // Server Error
        SimpleRequest req = gson.fromJson(request.body(), SimpleRequest.class);
        // ensure status 200 OK, with a MIME type of JSON
        // NB: even on error, we return 200, but with a JSON object that
        // describes the error.

        response.status(200);
        response.type("application/json");

        // Verify user identity
        // String uid = req.uid;
        // String session_id = req.session_id;

        // if (!session_id.equals(userTable.get(uid))) {
        //   return gson.toJson(
        //     new StructuredResponse("error", "invalid session_id", null)
        //   );
        // }
        // NB: createEntry checks for null title and message
        int newId = dataBase.insertIdea(req.title, req.idea, req.uid);
        if (newId == -1) {
          return gson.toJson(
            new StructuredResponse("error", "error performing insertion", null)
          );
        } else {
          return gson.toJson(new StructuredResponse("ok", "" + newId, null));
        }
      }
    );

    // POST route for adding a new element to the DataStore. This will read
    // JSON from the body of the request, turn it into a SimpleRequest
    // object, extract the title and message, insert them, and return the
    // ID of the newly created row.
    // takes a comment and adds a new comment to the comment table
    Spark.post(
      "/comment",
      (request, response) -> {
        System.out.println(request.body());
        // NB: if gson.Json fails, Spark will reply with status 500 Internal
        // Server Error
        SimpleRequest req = gson.fromJson(request.body(), SimpleRequest.class);
        // ensure status 200 OK, with a MIME type of JSON
        // NB: even on error, we return 200, but with a JSON object that
        // describes the error.
        response.status(200);
        response.type("application/json");

        // Verify user identity
        // String uid = req.uid;
        // String session_id = req.session_id;

        // if (!session_id.equals(userTable.get(uid))) {
        //   return gson.toJson(
        //     new StructuredResponse("error", "invalid session_id", null)
        //   );
        // }

        // NB: createEntry checks for null title and message
        int newId = dataBase.insertComment(req.comment, req.id, req.uid);
        if (newId == -1) {
          return gson.toJson(
            new StructuredResponse("error", "error performing insertion", null)
          );
        } else {
          return gson.toJson(new StructuredResponse("ok", "" + newId, null));
        }
      }
    );

    Spark.put(
      "/editcomment",
      (request, response) -> {
        System.out.println(request.body());
        // NB: if gson.Json fails, Spark will reply with status 500 Internal
        // Server Error
        SimpleRequest req = gson.fromJson(request.body(), SimpleRequest.class);
        // ensure status 200 OK, with a MIME type of JSON
        // NB: even on error, we return 200, but with a JSON object that
        // describes the error.
        response.status(200);
        response.type("application/json");

        // Verify user identity
        String uid = req.uid;
        String session_id = req.session_id;

        if (!session_id.equals(userTable.get(uid))) {
          return gson.toJson(
            new StructuredResponse("error", "invalid session_id", null)
          );
        }

        // NB: createEntry checks for null title and message
        int newId = dataBase.editComment(req.comment, req.cid);
        if (newId == -1) {
          return gson.toJson(
            new StructuredResponse("error", "error performing insertion", null)
          );
        } else {
          return gson.toJson(new StructuredResponse("ok", "" + newId, null));
        }
      }
    );

    // how to integrate likes into this?
    // can i make update one make the likes increment and update two make the likes
    // decrement
    // PUT route for updating a row in the DataStore. This is almost
    // exactly the same as POST
    // //
    Spark.put(
      "/likes/:id/:inc_dec/:uid/:up_down",
      (request, response) -> { // true or false for increment or
        // decrement likes
        // If we can't get an ID or can't parse the JSON, Spark will send
        // a status 500
        int id = Integer.parseInt(request.params("id"));
        int tf = Integer.parseInt(request.params("inc_dec"));
        String uid = request.params("uid");
        int up_down = Integer.parseInt(request.params("up_down"));
        SimpleRequest req = gson.fromJson(request.body(), SimpleRequest.class);
        // ensure status 200 OK, with a MIME type of JSON
        response.status(200);
        response.type("application/json");

        // Verify user identity
        String session_id = req.session_id;

        if (!session_id.equals(userTable.get(uid))) {
          return gson.toJson(
            new StructuredResponse("error", "invalid session_id", null)
          );
        }

        int result = dataBase.updateLikes(id, uid, tf, up_down);
        if (result == -1) {
          return gson.toJson(
            new StructuredResponse("error", "unable to update row " + id, null)
          );
        } else {
          return gson.toJson(new StructuredResponse("ok", null, result));
        }
      }
    );

    // DELETE route for removing a row from the DataStore
    // takes an idea id (int) and deletes it from database
    // Spark.delete(
    //   "/ideas/:id",
    //   (request, response) -> {
    //     // If we can't get an ID, Spark will send a status 500
    //     int idx = Integer.parseInt(request.params("id"));
    //     // ensure status 200 OK, with a MIME type of JSON
    //     response.status(200);
    //     response.type("application/json");
    //     // NB: we won't concern ourselves too much with the quality of the
    //     // message sent on a successful delete
    //     int result = dataBase.deleteIdea(idx);
    //     if (result == -1) {
    //       return gson.toJson(
    //         new StructuredResponse("error", "unable to delete row " + idx, null)
    //       );
    //     } else {
    //       return gson.toJson(new StructuredResponse("ok", null, null));
    //     }
    //   }
    // );

    Spark.post(
      "/register",
      (request, response) -> { // user_id, username, email, so, gi, note
        System.out.println(request.body());
        // NB: if gson.Json fails, Spark will reply with status 500 Internal
        // Server Error
        SimpleRequest req = gson.fromJson(request.body(), SimpleRequest.class);
        // ensure status 200 OK, with a MIME type of JSON
        // NB: even on error, we return 200, but with a JSON object that
        // describes the error.
        response.status(200);
        response.type("application/json");
        // NB: createEntry checks for null title and message
        int newId = dataBase.insertUser(
          req.uid,
          req.username,
          req.email,
          req.fname,
          req.lname,
          req.gi,
          req.so,
          req.note
        );
        if (newId == -1) {
          return gson.toJson(new StructuredResponse("error", "error", null));
        } else {
          return gson.toJson(new StructuredResponse("ok", "" + newId, null));
        }
      }
    );

    Spark.get(
      "/user/:uid",
      (request, response) -> {
        String uid = request.params("uid");
        response.status(200);
        response.type("application/json");
        UserRow user = dataBase.selectOneUser(uid);
        if (user == null) {
          return gson.toJson(new StructuredResponse("error", "error", null));
        } else {
          return gson.toJson(new StructuredResponse("ok", null, user));
        }
      }
    );

    Spark.post(
      "/userexists",
      (request, response) -> {
        System.out.println(request.body());
        // NB: if gson.Json fails, Spark will reply with status 500 Internal
        // Server Error
        SimpleRequest req = gson.fromJson(request.body(), SimpleRequest.class);
        // ensure status 200 OK, with a MIME type of JSON
        // NB: even on error, we return 200, but with a JSON object that
        // describes the error.
        response.status(200);
        response.type("application/json");
        // NB: createEntry checks for null title and message
        int userStatus = dataBase.login(req.uid, req.email);
        String session_id = String.valueOf((int) (Math.random() * 10000));
        JsonObject json = JsonParser.parseString("{}").getAsJsonObject();
        userTable.put(req.uid, session_id);
        json.add("userStatus", new JsonPrimitive(userStatus));
        json.add("session_id", new JsonPrimitive(session_id));
        if (userStatus == -1) {
          return gson.toJson(
            new StructuredResponse("ok", "user does not exist", json)
          );
        } else if (userStatus == 0) { // user exists
          return gson.toJson(
            new StructuredResponse("ok", "user exists " + userStatus, json)
          );
        }
        System.out.println("USER STATUS: " + userStatus);
        if (req.uid.length() > 0) {
          return gson.toJson(
            new StructuredResponse("ok", "userStatus: " + userStatus, json)
          );
        } else {
          return gson.toJson(new StructuredResponse("error", "error", json));
        }
      }
    );

    Spark.put(
      "/profile",
      (request, response) -> {
        System.out.println(request.body());
        // NB: if gson.Json fails, Spark will reply with status 500 Internal
        // Server Error
        SimpleRequest req = gson.fromJson(request.body(), SimpleRequest.class);
        // ensure status 200 OK, with a MIME type of JSON
        // NB: even on error, we return 200, but with a JSON object that
        // describes the error.
        response.status(200);
        response.type("application/json");
        // NB: createEntry checks for null title and message
        int result = dataBase.updateUserProf(
          req.uid,
          req.username,
          req.email,
          req.fname,
          req.lname,
          req.gi,
          req.so,
          req.note
        );
        if (result == -1) {
          return gson.toJson(new StructuredResponse("error", "error", null));
        } else {
          return gson.toJson(new StructuredResponse("ok", null, null));
        }
      }
    );

    Spark.post(
      "/login",
      (request, response) -> { // request contains code
        System.out.println(request.body());
        // NB: if gson.Json fails, Spark will reply with status 500 Internal
        // Server Error
        SimpleRequest req = gson.fromJson(request.body(), SimpleRequest.class);
        String code = req.code;
        // ensure status 200 OK, with a MIME type of JSON
        // NB: even on error, we return 200, but with a JSON object that
        // describes the error.
        response.status(200);
        response.type("application/json");
        System.out.println("CODE: " + code);

        String CLIENT_ID =
          "72850831242-dc9tqvcge6iqhjmr6bdjl0uq435asqrg.apps.googleusercontent.com";
        String CLIENT_SECRET = "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";
        String CALLBACK_URL = req.redirect;

        // Request a token with the authorization code

        TokenResponse token = new AuthorizationCodeTokenRequest(
          new NetHttpTransport(),
          new GsonFactory(),
          new GenericUrl("https://accounts.google.com/o/oauth2/token"),
          code
        )
          .setRedirectUri(CALLBACK_URL)
          .setGrantType("authorization_code")
          .setClientAuthentication(
            new BasicAuthentication(CLIENT_ID, CLIENT_SECRET)
          )
          .execute();

        HttpRequestFactory requestFactory = new NetHttpTransport()
          .createRequestFactory(
            new HttpRequestInitializer() {
              @Override
              public void initialize(HttpRequest request) {
                request
                  .getHeaders()
                  .setAuthorization("Bearer " + token.getAccessToken());
              }
            }
          );
        GenericUrl url = new GenericUrl(
          "https://www.googleapis.com/oauth2/v1/userinfo?alt=json"
        );
        HttpRequest r = requestFactory.buildGetRequest(url);
        r.getHeaders().setContentType("application/json");
        HttpResponse httpResponse = r.execute();
        String jsonIdentity = httpResponse.parseAsString();
        System.out.println(jsonIdentity);
        JsonObject json = JsonParser
          .parseString(jsonIdentity)
          .getAsJsonObject();
        String id = json.get("id").getAsString();
        String session_id = String.valueOf((int) (Math.random() * 10000));
        userTable.put(id, session_id);
        String email = json.get("email").getAsString();
        int userStatus = dataBase.login(id, email); // -1 for error, 0 for user exists, 1 for user does not exist
        json.add("userStatus", new JsonPrimitive(userStatus));
        json.add("session_id", new JsonPrimitive(session_id));
        if (userStatus == -1) {
          return gson.toJson(
            new StructuredResponse("ok", "user does not exist", json)
          );
        } else if (userStatus == 0) { // user exists
          return gson.toJson(
            new StructuredResponse("ok", "user exists " + userStatus, json)
          );
        }
        System.out.println("USER STATUS: " + userStatus);
        if (id.length() > 0) {
          return gson.toJson(
            new StructuredResponse("ok", "userStatus: " + userStatus, json)
          );
        } else {
          return gson.toJson(new StructuredResponse("error", "error", json));
        }
      }
    );
  }

  /**
   * Set up CORS headers for the OPTIONS verb, and for every response that the
   * server sends. This only needs to be called once.
   *
   * @param origin  The server that is allowed to send requests to this server
   * @param methods The allowed HTTP verbs from the above origin
   * @param headers The headers that can be sent with a request from the above
   *                origin
   */
  private static void enableCORS(
    String origin,
    String methods,
    String headers
  ) {
    // Create an OPTIONS route that reports the allowed CORS headers and methods
    Spark.options(
      "/*",
      (request, response) -> {
        String accessControlRequestHeaders = request.headers(
          "Access-Control-Request-Headers"
        );
        if (accessControlRequestHeaders != null) {
          response.header(
            "Access-Control-Allow-Headers",
            accessControlRequestHeaders
          );
        }
        String accessControlRequestMethod = request.headers(
          "Access-Control-Request-Method"
        );
        if (accessControlRequestMethod != null) {
          response.header(
            "Access-Control-Allow-Methods",
            accessControlRequestMethod
          );
        }
        return "OK";
      }
    );

    // 'before' is a decorator, which will run before any
    // get/post/put/delete. In our case, it will put three extra CORS
    // headers into the response
    Spark.before((request, response) -> {
      response.header("Access-Control-Allow-Origin", origin);
      response.header("Access-Control-Request-Method", methods);
      response.header("Access-Control-Allow-Headers", headers);
    });
  }
}

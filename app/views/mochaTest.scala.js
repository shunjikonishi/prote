@(desc: String, messages: Seq[models.testgen.MessageWrapper])
var http = require("http"),
	https = require("https"),
	assert = require("chai").assert;

describe("@desc", function() {
@messages.map { msg =>
	it("@msg.path", function(done) {
		function validateResponse(body) {
			//ToDo
		}
		var params = {
			"hostname": "@msg.host",
			"method": "@msg.method",
			"headers": @msg.requestHeaders(3)
		};
@if(msg.hasRequestBody) {
		var = requestData = buildBody("@msg.requestContentType", @msg.requestBody(3));
		params.headers["Content-Length"] = requestData.length;
}
		var con = @msg.protocol,
			req = con.request(params, function(res) {
				assert.equal(res.statusCode, @msg.statusCode);
				var body = "";
				res.on("data", function(data) { body += data;});
				res.on("end", function() { 
					validateResponse(body);
					done();
				});
			});
@if(msg.hasRequestBody) {
		req.write(requestData);
}
		req.end();
	});
}	
});
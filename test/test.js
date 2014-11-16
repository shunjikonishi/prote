
var http = require("http"),
	https = require("https"),
	assert = require("chai").assert;

describe("desc", function() {

	it("msg.path", function(done) {
		function validateResponse(body) {
			//ToDo
		}
		var params = {
			"hostname": "flt.backlog.jp",
			"method": "POST",
			"path": "/Login.action",
			"headers": {}
		};

		var requestData = buildBody("application/x-www-form-urlencoded", {});
		params.headers["Content-Length"] = requestData.length;

		var con = https,
			req = con.request(params, function(res) {
				assert.equal(res.statusCode, 302);
				var body = "";
				res.on("data", function(data) { body += data;});
				res.on("end", function() { 
					validateResponse(body);
					done();
				});
			});

		req.write(requestData);

		req.end();
	});

	it("msg.path", function(done) {
		function validateResponse(body) {
			//ToDo
		}
		var params = {
			"hostname": "flt.backlog.jp",
			"method": "GET",
			"path": "/dashboard",
			"headers": {}
		};

		var con = https,
			req = con.request(params, function(res) {
				assert.equal(res.statusCode, 200);
				var body = "";
				res.on("data", function(data) { body += data;});
				res.on("end", function() { 
					validateResponse(body);
					done();
				});
			});

		req.end();
	});

	it("msg.path", function(done) {
		function validateResponse(body) {
			//ToDo
		}
		var params = {
			"hostname": "flt.backlog.jp",
			"method": "GET",
			"path": "/globalbar/notifications/notReadCount",
			"headers": {}
		};

		var con = https,
			req = con.request(params, function(res) {
				assert.equal(res.statusCode, 200);
				var body = "";
				res.on("data", function(data) { body += data;});
				res.on("end", function() { 
					validateResponse(body);
					done();
				});
			});

		req.end();
	});

	it("msg.path", function(done) {
		function validateResponse(body) {
			//ToDo
		}
		var params = {
			"hostname": "flt.backlog.jp",
			"method": "GET",
			"path": "/star/list",
			"headers": {}
		};

		var con = https,
			req = con.request(params, function(res) {
				assert.equal(res.statusCode, 200);
				var body = "";
				res.on("data", function(data) { body += data;});
				res.on("end", function() { 
					validateResponse(body);
					done();
				});
			});

		req.end();
	});

	it("msg.path", function(done) {
		function validateResponse(body) {
			//ToDo
		}
		var params = {
			"hostname": "flt.backlog.jp",
			"method": "GET",
			"path": "/favicon.ico",
			"headers": {}
		};

		var con = https,
			req = con.request(params, function(res) {
				assert.equal(res.statusCode, 200);
				var body = "";
				res.on("data", function(data) { body += data;});
				res.on("end", function() { 
					validateResponse(body);
					done();
				});
			});

		req.end();
	});
	
});
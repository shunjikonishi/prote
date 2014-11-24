var app = angular.module('App', ['bgDirectives', 'ui.bootstrap'])
	.controller("MainController", function($scope, $filter, $modal) 
{
window.$scope = $scope;
	var LIST_MAX = 1000,
		MessageKind = (function() {
			var array = [
					"None",
					"Image",
					"Script",
					"HTML",
					"Json",
					"XML",
					"UrlEncoded",
					"Unknown"
				],
				ret = {};
			$.each(array, function(idx, value) {
				ret[value] = value;
			});
			return ret;
		})();
	function SelectedRequest() {
		var self = this,
			selected = null,
			reqExpanded = false,
			resExpanded = false;

		function isWs() {
			if (!selected) return false;
			return selected.protocol.indexOf("ws") == 0;
		}
		function isHttp() {
			if (!selected) return false;
			return selected.protocol.indexOf("http") == 0;
		}
		function current(v) {
			if (v === undefined) {
				return selected;
			} else {
				selected = v;
				reqExpanded = false;
				resExpanded = false;
				return self;
			}
		}
		function requestKind() {
			if (!selected) return MessageKind.Unknown;
			if (selected.reqKind) return selected.reqKind;
			return MessageKind.Unknown;
		}
		function responseKind() {
			if (!selected) return MessageKind.Unknown;
			if (selected.resKind) return selected.resKind;
			return MessageKind.Unknown;
		}
		function requestCanExpand() {
			var kind = requestKind();
			return kind == MessageKind.Json || 
				kind == MessageKind.UrlEncoded || 
				kind == MessageKind.XML;
		}
		function responseCanExpand() {
			var kind = responseKind();
			return kind == MessageKind.Json || 
				kind == MessageKind.UrlEncoded || 
				kind == MessageKind.XML;
		}
		function requestExpanded(v) {
			if (v === undefined) {
				return reqExpanded;
			} else {
				reqExpanded = v;
				return self;
			}
		}
		function responseExpanded(v) {
			if (v === undefined) {
				return resExpanded;
			} else {
				resExpanded = v;
				return self;
			}
		}
		$.extend(this, {
			"current": current,
			"isWs": isWs,
			"isHttp": isHttp,
			"requestKind": requestKind,
			"responseKind": responseKind,
			"requestCanExpand": requestCanExpand,
			"responseCanExpand": responseCanExpand,
			"requestExpanded": requestExpanded,
			"responseExpanded": responseExpanded
		});
	}
	function process(data) {
		data.desc = data.method + " " + data.uri;
		addData(data);
	}
	function processWS(data) {
		addData(data);
	}
	function addData(data) {
		$scope.$apply(function() {
			if (list.length > LIST_MAX) {
				list.shift();
			data.select = false;
			}
			data.http = data.protocol.indexOf("http") == 0;
			data.ws = !data.http;
			list.push(data);
			setTimeout(function() {
				var mainDiv = $("#main")[0];
				mainDiv.scrollTop = mainDiv.scrollHeight;
			}, 0);
		});
	}
	function init() {
		var uri = 
			(location.protocol === "https:" ? "wss://" : "ws://") + 
			location.host + 
			"/" + $scope.contextPath + "/ws";
		con = new room.Connection(uri);
		con.on("process", process);
		con.on("processWS", processWS);
	}
	function tableClass(request) {
		if (selected.current() && selected.current().id === request.id) {
			return "info";
		}
		if (request.status >= 400) {
			return "warning";
		}
		if (request.time >= 1000) {
			return "danger";
		}
		return "";
	}
	function clickTableRow(request) {
		selected.current(request);
		showRequest(false);
		showResponse(false);
	}
	function showRequest(expand) {
		request = selected.current();
		con.request({
			"command": "request", 
			"data" : {
				"id": request.id,
				"protocol": request.protocol,
				"expand": expand
			},
			"success": function(data) {
				$scope.$apply(function() {
					$scope.requestMessage = data;
					selected.requestExpanded(expand);
				});
			}
		});
	}
	function showResponse(expand) {
		request = selected.current();
		con.request({
			"command": "response", 
			"data" : {
				"id": request.id,
				"protocol": request.protocol,
				"expand": expand
			},
			"success": function(data) {
				$scope.$apply(function() {
					$scope.responseMessage = data;
					selected.responseExpanded(expand);
				});
			}
		});
	}
	function filterRow(value) {
		if (filters.image && value.resKind == MessageKind.Image) return false;
		if (filters.script && value.resKind == MessageKind.Script) return false;
		if (filters.html && value.resKind == MessageKind.HTML) return false;
		if (filters.status304 && value.status == 304) return false;
		if (filters.status404 && value.status == 404) return false;
		if (filters.search) {
			if (value.desc.indexOf(filters.search) == -1) return false;
		}
		return true;
	}
	function clear() {
		list = [];
		$scope.list = list;
		selected.current(null);
		$scope.requestMessage = null;
		$scope.responseMessage = null;
	}
	function generateTest() {
		var ids = [];
		$.each($filter("filter")(list, filterRow), function(idx, value) {
			if (value.select) {
				ids.push(value.id);
			}
		});
		if (ids.length === 0) {
			alert("Select rows to generate test.");
			return;
		}
		generateOption.title = "Generate test";
		generateOption.regenerate = false;
		generateOption.ids = ids;
		generateOption.external = "";
		$modal.open({
			templateUrl: "generate-option",
			scope: $scope,
			backdrop: "static"
		}).result.then(function(result) {
			if (result == "ok") {
				con.request({
					"command": "generateTest", 
					"data" : generateOption,
					"success": function(data) {
						generateOption.id = data;
						download(data);
					}
				});
			}
		});
	}
	function regenerateTest() {
		generateOption.title = "Regenerate test";
		generateOption.regenerate = true;
		generateOption.ids = null;
		$modal.open({
			templateUrl: "generate-option",
			scope: $scope,
			backdrop: "static"
		}).result.then(function(result) {
			if (result == "ok") {
				con.request({
					"command": "regenerateTest", 
					"data" : generateOption,
					"success": function(data) {
						if (data.error) {
							alert(data.error);
						} else {
							download(generateOption.id);
						}
					}
				});
			}
		});
	}
	function download(id) {
		location.href = "/" + $scope.contextPath + "/download/" + id;
	}
	function selectAll($event) {
		var checked = $event.target.checked;
		$.each($filter("filter")(list, filterRow), function(idx, value) {
			if (value.http) {
				value.select = checked;
			}
		});
	}
	function test() {
	}
	var con = null,
		list = [],
		selected = new SelectedRequest(),
		generateOption = {
			filename: "test",
			description: "Auto generate test",
			external: "",
			kind: "mocha"
		},
		filters = {
			"image": false,
			"script": false,
			"html": false,
			"status304": false,
			"status404": false,
			"search": ""
		}
	$.extend($scope, {
		"MessageKind": MessageKind,
		"list": list,
		"filters": filters,
		"selectedRequest": selected,
		"tableClass": tableClass,
		"clickTableRow": clickTableRow,
		"filterRow": filterRow,
		"showRequest": showRequest,
		"showResponse": showResponse,
		"clear": clear,
		"generateOption": generateOption,
		"generateTest": generateTest,
		"regenerateTest": regenerateTest,
		"selectAll": selectAll,
		"test": test,
		"requestMessage": null,
		"responseMessage": null
	});
	$(init)
});

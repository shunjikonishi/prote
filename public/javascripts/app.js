var app = angular.module('App', ['bgDirectives'])
	.controller("MainController", function($scope, $filter) 
{
	var LIST_MAX = 1000,
		MessageKind = (function() {
			var array = [
					"None",
					"Image",
					"Script",
					"HTML",
					"Json",
					"XML",
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
			reqPrettyPrint = false,
			resPrettyPrint = false;

		function current(v) {
			if (v === undefined) {
				return selected;
			} else {
				selected = v;
				reqPrettyPrint = false;
				resPrettyPrint = false;
				return self;
			}
		}
		function isRequestJson() {
			return !!selected && selected.reqKind === MessageKind.Json;
		}
		function isResponseJson() {
			return !!selected && selected.resKind === MessageKind.Json;
		}
		function requestPrettyPrint(v) {
			if (v === undefined) {
				return reqPrettyPrint;
			} else {
				reqPrettyPrint = v;
				return self;
			}
		}
		function responsePrettyPrint(v) {
			if (v === undefined) {
				return resPrettyPrint;
			} else {
				resPrettyPrint = v;
				return self;
			}
		}
		$.extend(this, {
			"current": current,
			"isRequestJson": isRequestJson,
			"isResponseJson": isResponseJson,
			"requestPrettyPrint": requestPrettyPrint,
			"responsePrettyPrint": responsePrettyPrint
		});
	}
	function process(data) {
		$scope.$apply(function() {
			if (list.length > LIST_MAX) {
				list.shift();
			}
			list.push(data);
			setTimeout(function() {
				var mainDiv = $("#main")[0];
				mainDiv.scrollTop = mainDiv.scrollHeight;
			}, 0);
		})
	}
	function init() {
		var uri = 
			(location.protocol === "https:" ? "wss://" : "ws://") + 
			location.host + 
			"/" + $scope.contextPath + "/ws";
		con = new room.Connection(uri);
		con.on("process", process);
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
	function showRequest(prettyPrint) {
		request = selected.current();
		con.request({
			"command": "request", 
			"data" : {
				"id": request.id,
				"prettyPrint": prettyPrint
			},
			"success": function(data) {
				$scope.$apply(function() {
					$scope.requestMessage = data;
					selected.requestPrettyPrint(prettyPrint);
				});
			}
		});
	}
	function showResponse(prettyPrint) {
		request = selected.current();
		con.request({
			"command": "response", 
			"data" : {
				"id": request.id,
				"prettyPrint": prettyPrint
			},
			"success": function(data) {
				$scope.$apply(function() {
					$scope.responseMessage = data;
					selected.responsePrettyPrint(prettyPrint);
				});
			}
		});
	}
	function filterRow(value) {
		if (filters.image && value.resKind == MessageKind.Image) return false;
		if (filters.script && value.resKind == MessageKind.Script) return false;
		if (filters.html && value.resKind == MessageKind.HTML) return false;
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
			ids.push(value.id);
		});
		con.request({
			"command": "generateTest", 
			"data" : {
				"ids": ids
			},
			"success": function(data) {
				location.href = "/" + $scope.contextPath + "/download/" + data;
			}
		});
	}
	function test() {
		var ids = ["0", "1", "2", "3", "4"];
		con.request({
			"command": "test", 
			"data" : {
				"ids": ids
			},
			"success": function(data) {
				location.href = "/" + $scope.contextPath + "/download/" + data;
			}
		});
	}
	var con = null,
		list = [],
		selected = new SelectedRequest(),
		filters = {
			"image": false,
			"script": false,
			"html": false
		}
	$.extend($scope, {
		"list": list,
		"filters": filters,
		"selectedRequest": selected,
		"tableClass": tableClass,
		"clickTableRow": clickTableRow,
		"filterRow": filterRow,
		"showRequest": showRequest,
		"showResponse": showResponse,
		"clear": clear,
		"generateTest": generateTest,
		"test": test,
		"requestMessage": null,
		"responseMessage": null
	});
	$(init)
});

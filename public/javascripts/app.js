var app = angular.module('App', ['bgDirectives', 'ui.bootstrap'])
	.controller("MainController", function($scope, $filter, $modal) 
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
			data.select = false;
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
		if (filters.status304 && value.status == 304) return false;
		if (filters.status404 && value.status == 404) return false;
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
			value.select = checked;
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
			kind: "mocha"
		},
		filters = {
			"image": false,
			"script": false,
			"html": false,
			"status304": false,
			"status404": false
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

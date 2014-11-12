var app = angular.module('App', ['bgDirectives'])
	.controller("MainController", function($scope) 
{
	var LIST_MAX = 1000;
	function process(data) {
		$scope.$apply(function() {
			if ($scope.list.length > LIST_MAX) {
				$scope.list.shift();
			}
			$scope.list.push(data);
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
		if (request.status >= 400) {
			return "warning";
		}
		if (request.time >= 1000) {
			return "danger";
		}
		return "";
	}
	function clickTableRow(request) {
		con.request({
			"command": "request", 
			"data" : {
				"id": request.id
			},
			"success": function(data) {
				$scope.$apply(function() {
					$scope.requestMessage = data;
				});
			}
		});
		con.request({
			"command": "response", 
			"data" : {
				"id": request.id
			},
			"success": function(data) {
				$scope.$apply(function() {
					$scope.responseMessage = data;
				});
			}
		});
	}
	var con = null;
	$.extend($scope, {
		"list": [],
		"tableClass": tableClass,
		"clickTableRow": clickTableRow
	});
	$(init)
});

var app = angular.module('App', ['bgDirectives'])
	.controller("MainController", function($scope) 
{
	function init() {
		var uri = 
			(location.protocol === "https:" ? "wss://" : "ws://") + 
			location.host + 
			"/" + $scope.contextPath + "/ws";
		con = new room.Connection(uri);
		con.on("process", function(data) {
			$scope.$apply(function() {
				$scope.list.push(data);
			})
			console.log(data);
		});
	}
	var con = null;
	$scope.list = [];
	$(init)
});

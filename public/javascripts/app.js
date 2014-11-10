var app = angular.module('App', ['bgDirectives'])
	.controller("MainController", function($scope) {
		console.log("test: " + $scope.sessionId);
	});
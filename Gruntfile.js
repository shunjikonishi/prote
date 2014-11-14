module.exports = function (grunt) {
	'use strict';

	function loadDependencies(deps) {
		if (deps) {
			for (var key in deps) {
				if (key.indexOf("grunt-") == 0) {
					grunt.loadNpmTasks(key);
				}
			}
		}
	}

	grunt.initConfig({
		pkg: grunt.file.readJSON('package.json'),
		copy: {
			bower: {
				expand: true,
				flatten: true,
				cwd: '',
				src: ['bower_components/angular/*.js', 'bower_components/angular/*.map', 'bower_components/bg-splitter/js/*.js'],
				dest: 'public/javascripts/ext'
			}
		},
		jshint : {
			test : ['test/*.js']
		}
	});

	loadDependencies(grunt.config("pkg").devDependencies);

	grunt.registerTask('default', ['copy']);
};

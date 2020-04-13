default: build


build:
	docker build . -t omnyway/vulcan

release:
	docker tag omnyway/vulcan omnyway/vulcan:latest
	docker tag omnyway/vulcan omnyway/vulcan:$(ver)
	docker push omnyway/vulcan:$(ver)
	docker push omnyway/vulcan:latest

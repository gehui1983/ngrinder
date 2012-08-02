# A simple example using the HTTP plugin that shows the retrieval of a
# single page via HTTP. The resulting page is written to a file.
#
# More complex HTTP scripts are best created with the TCPProxy.
#
# This script is auto generated by ngrinder.
#
# @author ${user.userName}
from net.grinder.script.Grinder import grinder
from net.grinder.script import Test
from net.grinder.plugin.http import HTTPRequest

test1 = Test(1, "Request resource")
request1 = test1.wrap(HTTPRequest())

class TestRunner:
	def __call__(self):
		returnValue = request1.GET("http://www.naver.com")

		# result is a HTTPClient.HTTPResult. We get the message body
		# using the getText() method.
		print returnValue
        
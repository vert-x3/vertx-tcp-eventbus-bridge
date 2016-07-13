import Eventbus.Eventbus as Eventbus
import Eventbus.DeliveryOption as DeliveryOption
import json

#replyHanlder (self,error,message)
#handlers (self,message)

class TimeClient:
			
	#replyHandler for "Get.Time"
	def printTime(self,error,message):
		if error != None:
			print(error)
		if message != None:
			print(message['body'])
		
	#replyHandler for "Get.Date"
	def printDate(self,error,message):
		if error != None:
			print(error)
		if message != None:
			print(message['body'])
			
	#Handler for errors
	def Handler(self,message):
		if message != None:
			print('Update:')
			print(message['body'])
			
	
eb=Eventbus.Eventbus('localhost', 7000)	

#jsonObject -body
body_date={'message':'send date',}

#DeliveryOption
do=DeliveryOption.DeliveryOption();
do.addHeader('type','text')
do.addHeader('size','small')
do.addReplyAddress('Date')
do.setTimeInterval(1) 

#send - get data
eb.send('Date',body_date,do,TimeClient.printDate)

#jsonObject -body
body_time={'message':'send time',}

#change reply address
do.addReplyAddress('Time')

#send- get time
eb.send('Time',body_time,do,TimeClient.printTime)

#change reply address
do.addReplyAddress('Get')

#register handler
eb.registerHandler('Get',do,TimeClient.Handler);

#jsonObject -body
body_get={'message':'Thanks time keeper',}

#publish- get 
eb.publish('Get',body_get,do)

#send- get time
eb.send('Time',body_time,do,TimeClient.printTime)
#send - get data
eb.send('Date',body_date,do,TimeClient.printDate)

#close after 5 seconds
eb.closeConnection(5)
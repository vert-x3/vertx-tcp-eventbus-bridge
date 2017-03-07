
CFLAGS = -w -g

INCLUDES = -I./include

ifeq ($(TARGET), unix)
  #this is for Unix/Linux. Comment this if you are using Windows
  LIBS = -lpthread
else
  #this is for windows. Comment this if you are using Unix/Linux
  LIBS = -lws2_32 --enable-stdcall-fixup
endif

SRCS =  lib/parson.c  lib/osi_socket.c vertx.c

OBJS = $(SRCS:.c=.o)

MAIN = example

.PHONY: depend clean

all:    $(MAIN)

$(MAIN): eventbus.a test/test.o
		$(CC) -L. test/test.o -leventbus $(LIBS) -o $(MAIN)

eventbus.a: $(OBJS)
		$(AR) crv libeventbus.a $(OBJS)
		@ranlib libeventbus.a

.c.o:
		$(CC) $(CFLAGS) $(INCLUDES) -c $<  -o $@

clean:
		$(RM) $(OBJS) test/test.o *.a *~ $(MAIN)

depend: $(SRCS)
		makedepend $(INCLUDES) $^

# DO NOT DELETE THIS LINE -- make depend needs it

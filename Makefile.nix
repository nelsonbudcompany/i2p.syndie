#
# $os version:
GCJ=gcj
#GCJ=/u1/gcc-4.2-20060520-x86_64/bin/gcj
EXECUTABLE=syndie
GCJFLAGS=-g -O2 -fPIC -fjni -Wall
CURVERSION=`cat VERSION | cut -d= -f2`
PKGEND="nix.tar.bz2"

all: syndie

hsqldb_gcj.jar:
	@rm -rf hsql
	@mkdir hsql
	@echo "building GCJ-friendly hsqldb_gcj.jar"
	@(cd hsql ;\
        	jar xf ../lib/hsqldb.jar ;\
	        rm -f org/hsqldb/util/DatabaseManager*.class \
		org/hsqldb/util/TableSorter*.class \
		org/hsqldb/HsqlSocketFactorySecure.class \
		org/hsqldb/persist/NIOLockFile.class \
		org/hsqldb/util/*Swing*.class ;\
		jar cfm ../hsqldb_gcj.jar META-INF/MANIFEST.MF *class org ;\
	)

hsqldb_gcj.o: hsqldb_gcj.jar
	${GCJ} ${GCJFLAGS} -c -o hsqldb_gcj.o lib/servlet.jar hsqldb_gcj.jar 

clean:
	@rm -f lib/syndie.jar syndie.o
	@rm -f ${EXECUTABLE} syndie.o

distclean: clean
	@rm -f hsqldb_gcj.o
	@rm -rf hsql hsqldb_gcj.jar
	@rm -rf syndie-${CURVERSION}
	@rm -f syndie-${CURVERSION}.${PKGEND}
	@rm -f doc/web/dist/syndie-${CURVERSION}.${PKGEND}
	@ant distclean
	@rm -rf logs

lib/syndie.jar:
	@echo "Compiling syndie"
	@ant -q jar

syndie.o: lib/syndie.jar
	${GCJ} ${GCJFLAGS} -c lib/syndie.jar

${EXECUTABLE}: hsqldb_gcj.o syndie.o
	${GCJ} ${GCJFLAGS} -o ${EXECUTABLE} --main=syndie.db.TextUI hsqldb_gcj.o syndie.o

package: ${EXECUTABLE}
	@ant -q prep-java
	@rm -f syndie-${CURVERSION}/bin/syndie
	@rm -f syndie-${CURVERSION}/bin/syndie.bat
	@rm -f syndie-${CURVERSION}/lib/*jar
	@strip ${EXECUTABLE}
	@cp ${EXECUTABLE} syndie-${CURVERSION}/bin/
	@mkdir -p doc/web/dist
	@tar cjf doc/web/dist/syndie-${CURVERSION}.${PKGEND} syndie-${CURVERSION}
	@rm -rf syndie-${CURVERSION}
	@echo "Native package built into doc/web/dist/syndie-${CURVERSION}.${PKGEND}"
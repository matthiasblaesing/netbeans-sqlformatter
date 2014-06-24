SELECT * FROM sometable WHERE ( a = NOW() )
-------------
SELECT * FROM sometable WHERE ( a = NOW() )
-------------
SELECT * FROM sometable WHERE ( ( a = NOW() ) )
-------------
SELECT * FROM sometable WHERE ( ( a = NOW() ) )
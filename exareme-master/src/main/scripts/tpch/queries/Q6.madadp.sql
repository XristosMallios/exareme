distributed create temporary table lineitem_partial to 1 as
select sum(l_extendedprice * l_discount) as revenue_PARTIAL
from lineitem
where l_shipdate >= '1994-01-01'
  and l_shipdate < '1995-01-01'
  and l_discount > 0.07
  and l_discount < 0.09
  and l_quantity < 24;

distributed create temporary table q6_result_8_temp as
select sum(revenue_PARTIAL) as revenue
from lineitem_partial;

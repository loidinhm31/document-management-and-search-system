import React from "react";

import { Skeleton } from "@/components/ui/skeleton";
import { TableCell, TableRow } from "@/components/ui/table";

interface TableSkeletonProps {
  rows: number;
  cells: number;
}

const TableSkeleton: React.FC<TableSkeletonProps> = ({ rows, cells }) => {
  return (
    <>
      {Array(rows)
        .fill(null)
        .map((_, rowIndex) => (
          <TableRow key={`loading-${rowIndex}`}>
            {Array(cells)
              .fill(null)
              .map((_, cellIndex) => (
                <TableCell key={`loading-cell-${cellIndex}`}>
                  <Skeleton className="h-6 w-full" />
                </TableCell>
              ))}
          </TableRow>
        ))}
    </>
  );
};

export default TableSkeleton;

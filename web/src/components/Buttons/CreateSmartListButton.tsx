import React from 'react';
import { Button, Tooltip } from '@material-ui/core';
import { OfflineBolt, Save } from '@material-ui/icons';
import { FilterParams, isDefaultFilter } from '../../utils/searchFilters';
import { filterParamsEqual } from '../../utils/changeDetection';
import useIsMobile from '../../hooks/useIsMobile';

interface Props {
  onClick: () => void;
  isListDynamic?: boolean;
  filters: FilterParams;
  listFilters?: FilterParams;
}

export default function CreateSmartListButton(props: Props) {
  const isMobile = useIsMobile();

  return (
    <Tooltip
      title={
        props.isListDynamic
          ? 'Save changes to your smart list'
          : 'Save this search as a dynamic list'
      }
      aria-label="save-as-list"
      placement="top"
    >
      <React.Fragment>
        <Button
          size="small"
          disabled={
            props.isListDynamic
              ? isDefaultFilter(props.filters) ||
                filterParamsEqual(props.listFilters!, props.filters)
              : false
          }
          onClick={props.onClick}
          variant="contained"
          color="primary"
          aria-label={
            props.isListDynamic ? 'Save Smart List' : 'Create Smart List'
          }
          startIcon={props.isListDynamic ? <Save /> : <OfflineBolt />}
          fullWidth={isMobile}
        >
          {props.isListDynamic ? 'Save Smart List' : 'Create Smart List'}
        </Button>
      </React.Fragment>
    </Tooltip>
  );
}

import React from 'react';
import { Button, makeStyles } from '@material-ui/core';
import { AddCircle, List as ListIcon } from '@material-ui/icons';
import { Item, itemBelongsToLists } from '../../types/v2/Item';
import { useWidth } from '../../hooks/useWidth';

const useStyles = makeStyles(theme => ({
  button: {
    marginTop: theme.spacing(1),
  },
  buttonIcon: {
    marginRight: theme.spacing(1),
    [theme.breakpoints.down('sm')]: {
      fontSize: '1rem',
    },
    fontSIze: '2rem',
  },
  itemCTA: {
    width: '100%',
    whiteSpace: 'nowrap',
  },
}));

interface Props {
  itemDetail?: Item;
  onClick: () => void;
  style?: object;
  cta?: string;
}

export default function ManageTrackingButton(props: Props) {
  const classes = useStyles();
  const width = useWidth();
  const isMobile = ['xs', 'sm'].includes(width);

  const belongsToLists: string[] =
    props && props.itemDetail ? itemBelongsToLists(props.itemDetail) : [];

  let trackingCTA =
    belongsToLists.length > 0 ? 'Manage Tracking' : 'Add to List';
  let trackingCTAMobile = 'Track';

  return (
    <div className={classes.itemCTA} style={{ ...props.style }}>
      <Button
        size="small"
        variant="contained"
        fullWidth
        aria-label={props.cta || trackingCTA}
        onClick={props.onClick}
        className={classes.button}
        startIcon={
          !isMobile ? (
            <ListIcon className={classes.buttonIcon} />
          ) : (
            <AddCircle />
          )
        }
      >
        {props.cta || isMobile ? trackingCTAMobile : trackingCTA}
      </Button>
    </div>
  );
}

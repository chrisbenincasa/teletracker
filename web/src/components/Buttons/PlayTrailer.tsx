import React, { useState } from 'react';
import { Backdrop, Button, Fade, makeStyles, Modal } from '@material-ui/core';
import { PlayArrow } from '@material-ui/icons';
import { Item } from '../../types/v2/Item';

const useStyles = makeStyles(theme => ({
  buttonIcon: {
    [theme.breakpoints.down('sm')]: {
      fontSize: '1rem',
    },
    fontSze: '2rem',
  },
  close: {
    padding: theme.spacing(0.5),
  },
  itemCTA: {
    whiteSpace: 'nowrap',
  },
  modal: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  trailerVideo: {
    width: '60vw',
    height: '34vw',
    [theme.breakpoints.down('sm')]: {
      width: '100vw',
      height: '56vw',
    },
  },
}));

interface Props {
  style?: object;
  cta?: string;
  videoSourceId: string;
}

export interface State {
  open: boolean;
}

export default function ShareButton(props: Props) {
  const classes = useStyles();
  const [trailerModalOpen, setTrailerModalOpen] = useState<boolean>(false);

  return (
    <React.Fragment>
      <div className={classes.itemCTA} style={{ ...props.style }}>
        <Button
          size="small"
          variant="contained"
          fullWidth
          aria-label={'Play Trailer'}
          onClick={() => setTrailerModalOpen(true)}
          startIcon={<PlayArrow className={classes.buttonIcon} />}
        >
          {props.cta || 'Play Trailer'}
        </Button>
      </div>
      <Modal
        aria-labelledby="transition-modal-title"
        aria-describedby="transition-modal-description"
        className={classes.modal}
        open={trailerModalOpen}
        onClose={() => setTrailerModalOpen(false)}
        closeAfterTransition
        BackdropComponent={Backdrop}
        BackdropProps={{
          timeout: 500,
        }}
        style={{ backgroundColor: 'rgba(0, 0, 0, 0.8)' }}
      >
        <Fade in={trailerModalOpen}>
          <iframe
            width="600"
            height="338"
            src={`https://www.youtube.com/embed/${props.videoSourceId}?autoplay=1`}
            frameBorder="0"
            allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
            className={classes.trailerVideo}
          />
        </Fade>
      </Modal>
    </React.Fragment>
  );
}

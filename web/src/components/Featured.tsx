import React, { useEffect, useState, RefObject } from 'react';
import {
  CardMedia,
  Fade,
  Grow,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { Rating } from '@material-ui/lab';
import RouterLink from 'next/link';
import imagePlaceholder from '../../public/images/imagePlaceholder.png';
import ManageTracking from '../components/ManageTracking';
import { ResponsiveImage } from './ResponsiveImage';
import { Item } from '../types/v2/Item';
import AddToListDialog from './Dialogs/AddToListDialog';
import withUser, { WithUserProps } from './withUser';
import {
  formatRuntime,
  getVoteAverage,
  getVoteCount,
  truncateText,
} from '../utils/textHelper';
import { hexToRGB } from '../utils/style-utils';

const useStyles = makeStyles((theme: Theme) => ({
  backdropContainer: {
    height: 'auto',
    overflow: 'hidden',
    top: 0,
    width: '100%',
  },
  featuredItem: {
    position: 'relative',
    margin: theme.spacing(1),
    [theme.breakpoints.down('sm')]: {
      margin: 0,
    },
  },
  posterContainer: {
    display: 'flex',
    flex: '0 1 auto',
    flexDirection: 'column',
    position: 'absolute',
    height: 'auto',
    top: '5%',
    left: theme.spacing(3),
    width: '25%',
    [theme.breakpoints.up('md')]: {
      width: '30%',
    },
  },
  ratingContainer: {
    display: 'flex',
    flexDirection: 'row',
  },
  ratingVoteCount: {
    marginRight: theme.spacing(1),
    [theme.breakpoints.down('sm')]: {
      display: 'none',
    },
  },
  title: {
    textAlign: 'right',
    alignSelf: 'flex-end',
    fontSize: theme.typography.h5.fontSize,
    fontWeight: theme.typography.fontWeightBold,
  },
  titleContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    position: 'absolute',
    bottom: 0,
    right: 0,
    padding: theme.spacing(1),
    marginBottom: theme.spacing(2),
    backgroundColor: hexToRGB(theme.palette.grey[900], 0.65),
    maxWidth: '50%',
  },
  wrapper: {
    display: 'flex',
    flexDirection: 'row',
    margin: theme.spacing(2),
  },
}));

interface OwnProps {
  featuredItems: Item[];
}

type Props = OwnProps & WithUserProps;

function Featured(props: Props) {
  const [manageTrackingModalOpen, setManageTrackingModalOpen] = useState<
    boolean
  >(false);
  const [imageLoading, setImageLoading] = useState<boolean>(true);
  const classes = useStyles();
  const { featuredItems, userSelf } = props;

  useEffect(() => {
    setImageLoading(true);
    return setImageLoading(false);
  }, [featuredItems]);

  const renderTitle = (item: Item) => {
    const voteAverage = getVoteAverage(item);
    const voteCount = getVoteCount(item);
    const runtime =
      (item.runtime && formatRuntime(item.runtime, item.type)) || null;

    return (
      <div className={classes.titleContainer}>
        <Typography color="inherit" variant="h4" itemProp="name">
          {truncateText(item.canonicalTitle, 200)}
        </Typography>
        <div className={classes.ratingContainer}>
          <Rating value={voteAverage} precision={0.1} readOnly />
          <Typography
            color="inherit"
            variant="body1"
            className={classes.ratingVoteCount}
          >
            {`(${voteCount})`}
          </Typography>
        </div>
        <Typography color="inherit" variant="body1" itemProp="duration">
          {runtime}
        </Typography>
      </div>
    );
  };

  const imageLoaded = () => {
    setImageLoading(false);
  };

  const renderFeatuedItem = item => {
    const WrappedCardMedia = React.forwardRef(({ onClick, href }: any, ref) => {
      return (
        <a
          href={href}
          onClick={onClick}
          ref={ref as RefObject<HTMLAnchorElement>}
          style={{
            display: 'block',
            height: '100%',
            textDecoration: 'none',
          }}
        >
          <CardMedia
            src={imagePlaceholder}
            item={item}
            component={ResponsiveImage}
            imageType="poster"
            imageStyle={{
              boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
              maxWidth: '100%',
              borderRadius: 10,
            }}
            pictureStyle={{
              height: '100%',
              width: '100%',
              display: 'block',
            }}
          />
        </a>
      );
    });

    return (
      <Fade in={!imageLoading} key={item.id}>
        <div
          className={classes.featuredItem}
          style={{ width: `${100 / featuredItems.length}%` }}
        >
          <div className={classes.backdropContainer}>
            <ResponsiveImage
              item={item}
              imageType="backdrop"
              imageStyle={{
                objectFit: 'cover',
                width: '100%',
                height: '100%',
                borderRadius: 10,
              }}
              pictureStyle={{
                display: 'block',
              }}
              loadCallback={imageLoaded}
            />
          </div>

          <div className={classes.posterContainer}>
            <RouterLink href={item.canonicalUrl} as={item.relativeUrl} passHref>
              <WrappedCardMedia item={item} />
            </RouterLink>
            <ManageTracking itemDetail={item} />

            <AddToListDialog
              open={manageTrackingModalOpen}
              onClose={() => setManageTrackingModalOpen(false)}
              userSelf={userSelf!}
              item={item}
            />
          </div>
          <Grow in={!imageLoading} timeout={500}>
            {renderTitle(item)}
          </Grow>
        </div>
      </Fade>
    );
  };

  const renderFeaturedItems = () => {
    return featuredItems && featuredItems.length > 0
      ? featuredItems.map(item => {
          return renderFeatuedItem(item);
        })
      : null;
  };

  return featuredItems && featuredItems.length > 0 ? (
    <div className={classes.wrapper}>{renderFeaturedItems()}</div>
  ) : null;
}

export default withUser(Featured);

import React, { RefObject, useEffect, useState } from 'react';
import {
  CardMedia,
  Fade,
  Grow,
  makeStyles,
  Theme,
  Tooltip,
  Typography,
  useTheme,
} from '@material-ui/core';
import RouterLink from 'next/link';
import imagePlaceholder from '../../public/images/imagePlaceholder.png';
import ResponsiveImage from './ResponsiveImage';
import { Item } from '../types/v2/Item';
import AddToListDialog from './Dialogs/AddToListDialog';
import Rating from './Rating';
import { formatRuntime, truncateText } from '../utils/textHelper';
import { hexToRGB } from '../utils/style-utils';
import { AccessTime } from '@material-ui/icons';
import dequal from 'dequal';
import { Id } from '../types/v2';
import useStateSelector from '../hooks/useStateSelector';
import selectItems from '../selectors/selectItems';
import useIsMobile from '../hooks/useIsMobile';

const useStyles = makeStyles((theme: Theme) => ({
  backdropContainer: {
    height: '100%',
    overflow: 'hidden',
    top: 0,
    width: '100%',
  },
  featuredItem: {
    position: 'relative',
    display: 'inline-block',
    margin: theme.spacing(1),
    [theme.breakpoints.down('sm')]: {
      margin: 0,
    },
  },
  manageTrackingButton: {
    marginTop: theme.spacing(1),
  },
  posterContainer: {
    position: 'absolute',
    height: '100%',
    padding: theme.spacing(2),
    top: theme.spacing(0),
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
    borderTopLeftRadius: 10,
    borderBottomLeftRadius: 10,
    [theme.breakpoints.down('sm')]: {
      marginBottom: 0,
      borderBottomLeftRadius: 0,
    },
  },
  wrapper: {
    display: 'flex',
    flexDirection: 'row',
    position: 'relative',
    margin: theme.spacing(2),
    [theme.breakpoints.down('sm')]: {
      margin: theme.spacing(1),
    },
  },
  runtimeContainer: {
    paddingTop: theme.spacing(0.5),
  },
  runtimeSpan: {
    display: 'inline-block',
    paddingLeft: theme.spacing(0.5),
    verticalAlign: 'middle',
  },
}));

interface Props {
  readonly featuredItems: Id[];
}

function Featured(props: Props) {
  const [manageTrackingModalOpen, setManageTrackingModalOpen] = useState<
    boolean
  >(false);
  const [imageLoading, setImageLoading] = useState<boolean>(true);
  const classes = useStyles();
  const isMobile = useIsMobile();
  const { featuredItems } = props;
  const theme: Theme = useTheme();
  const items = useStateSelector(state => selectItems(state, featuredItems));

  useEffect(() => {
    setImageLoading(true);
  }, []);

  const renderTitle = (item: Item) => {
    const runtime =
      (item.runtime && formatRuntime(item.runtime, item.type)) || null;
    const itemType = item.type || 'item';

    return (
      <div className={classes.titleContainer}>
        <Typography
          color="inherit"
          variant={isMobile ? 'h5' : 'h4'}
          itemProp="name"
        >
          {truncateText(item.canonicalTitle, 200)}
        </Typography>
        <Rating itemId={item.id} />
        {runtime ? (
          <Typography
            className={classes.runtimeContainer}
            color="inherit"
            variant="body1"
            itemProp="duration"
          >
            <Tooltip title={`Runtime for this ${itemType}`} placement={'top'}>
              <AccessTime fontSize="small" />
            </Tooltip>
            <span className={classes.runtimeSpan}>{runtime}</span>
          </Typography>
        ) : null}
      </div>
    );
  };

  const imageLoaded = () => {
    setImageLoading(false);
  };

  const renderFeaturedItem = item => {
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
              maxHeight: '100%',
            }}
          />
        </a>
      );
    });

    // This calculates the spaces between multiple features items, 0 if only 1
    const calcPadding =
      featuredItems.length > 1 ? featuredItems.length * theme.spacing(1) : 0;

    return (
      <Fade in={!imageLoading} key={item.id}>
        <div
          className={classes.featuredItem}
          style={{
            width: `calc(${100 / featuredItems.length}% - ${calcPadding}px)`,
          }}
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
                pointerEvents: 'none', // Disables ios preview on tap & hold
              }}
              loadCallback={imageLoaded}
            />
          </div>

          <div className={classes.posterContainer}>
            <RouterLink href={item.canonicalUrl} as={item.relativeUrl} passHref>
              <WrappedCardMedia item={item} />
            </RouterLink>
          </div>
          <Grow in={!imageLoading} timeout={500}>
            {renderTitle(item)}
          </Grow>
        </div>
      </Fade>
    );
  };

  const renderFeaturedItems = () => {
    return items && items.length > 0
      ? items.map(item => {
          return renderFeaturedItem(item);
        })
      : null;
  };

  return items && items.length > 0 ? (
    <div
      className={classes.wrapper}
      style={{ paddingTop: `${56.25 / items.length}%` }}
    >
      <div style={{ position: 'absolute', top: 0, display: 'flex' }}>
        {renderFeaturedItems()}
      </div>
    </div>
  ) : null;
}

// DEV MODE ONLY
// Featured.whyDidYouRender = true;

export default React.memo(Featured, dequal);
